package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.entity.KbDocument;
import com.example.rag.mapper.KbDocumentMapper;
import com.example.rag.mq.IngestMessage;
import com.example.rag.mq.OcrRequestMessage;
import com.example.rag.mq.OcrRequestProducer;
import com.example.rag.support.ContentHash;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档入库流水线：MinIO 取回原件 -> 解析文本 -> 两级分片 -> 向量化 -> 写入 Qdrant。
 *
 * <p>普通文档用 Tika 解析；图片无法解析出文本，转由 Python(PaddleOCR) 服务识别后再入库。
 * <p>两条链路都由 RocketMQ 消费者触发，不阻塞上传接口。
 * <p>分片采用“父子两级”：子块入向量库参与相似度计算，父块正文随子块一起入库、命中后作为 Prompt 输入。
 * <p>每个子块还会由大模型预生成若干“假设问题”一并入库，作为额外的检索入口。
 */
@Service
public class DocumentIngestService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestService.class);

    /** 向量条目类型：子块正文 */
    private static final String VECTOR_TYPE_CONTENT = "content";

    /** 向量条目类型：由子块生成的假设问题 */
    private static final String VECTOR_TYPE_QUESTION = "question";

    /** 子块上附带的父块正文，命中后作为 Prompt 输入 */
    private static final String METADATA_PARENT_TEXT = "parentText";

    private final KbDocumentMapper documentMapper;

    private final StorageService storageService;

    private final DocumentParser documentParser;

    private final DocumentSplitter documentSplitter;

    private final DocumentSplitter parentDocumentSplitter;

    private final EmbeddingModel embeddingModel;

    private final EmbeddingStore<TextSegment> embeddingStore;

    private final OcrRequestProducer ocrRequestProducer;

    private final OcrClient ocrClient;

    private final HypotheticalQuestionService hypotheticalQuestionService;

    private final RagProperties properties;

    public DocumentIngestService(KbDocumentMapper documentMapper, StorageService storageService,
                                 DocumentParser documentParser,
                                 @Qualifier("documentSplitter") DocumentSplitter documentSplitter,
                                 @Qualifier("parentDocumentSplitter") DocumentSplitter parentDocumentSplitter,
                                 EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore,
                                 OcrRequestProducer ocrRequestProducer, OcrClient ocrClient,
                                 HypotheticalQuestionService hypotheticalQuestionService,
                                 RagProperties properties) {
        this.documentMapper = documentMapper;
        this.storageService = storageService;
        this.documentParser = documentParser;
        this.documentSplitter = documentSplitter;
        this.parentDocumentSplitter = parentDocumentSplitter;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.ocrRequestProducer = ocrRequestProducer;
        this.ocrClient = ocrClient;
        this.hypotheticalQuestionService = hypotheticalQuestionService;
        this.properties = properties;
    }

    public void ingest(IngestMessage message) {
        KbDocument document = documentMapper.selectById(message.getDocumentId());
        if (document == null) {
            log.warn("文档不存在，跳过入库：documentId={}", message.getDocumentId());
            return;
        }

        // 早期数据没有内容摘要，这里按原件补齐，后续上传即可据此比对内容是否变化
        if (!StringUtils.hasText(document.getContentHash())) {
            backfillContentHash(document);
        }

        updateStatus(document, KbDocument.STATUS_PROCESSING, null, null);

        // 图片用 Tika 解析不出文本，转交 OCR 链路：识别完成后由 ingestByOcr 继续
        if (properties.getOcr().isImage(document.getFileName())) {
            ocrRequestProducer.send(new OcrRequestMessage(document.getId(), document.getKbId(),
                    document.getObjectKey(), document.getFileName(), message.isReindex()));
            return;
        }

        try {
            List<TextSegment> chunks = parseAndSplit(document);
            embedAndStore(document, withHypotheticalQuestions(chunks), chunks.size(), message.isReindex());
        } catch (Exception e) {
            log.error("文档入库失败：documentId={}", document.getId(), e);
            updateStatus(document, KbDocument.STATUS_FAILED, null, trim(e.getMessage()));
        }
    }

    /**
     * OCR 识别完成后的入库：把识别出的文本按与普通文档完全一致的方式分片与向量化，
     * 因此图片内容同样能被智能问答检索到。
     */
    public void ingestByOcr(OcrRequestMessage message) {
        KbDocument document = documentMapper.selectById(message.getDocumentId());
        if (document == null) {
            log.warn("文档不存在，跳过 OCR 入库：documentId={}", message.getDocumentId());
            return;
        }

        updateStatus(document, KbDocument.STATUS_PROCESSING, null, null);
        try {
            String text;
            try (InputStream in = storageService.download(message.getObjectKey())) {
                // 识别出表格时入库用的是 Markdown，让分片保留行列结构，问答时能对齐到具体单元格
                text = ocrClient.recognize(message.getFileName(), in.readAllBytes(),
                        document.getContentType()).preferredText();
            }
            if (!StringUtils.hasText(text)) {
                throw new IllegalStateException("OCR 未从图片中识别出文字内容");
            }
            List<TextSegment> chunks = applyMetadata(Document.from(text), document);
            embedAndStore(document, withHypotheticalQuestions(chunks), chunks.size(), message.isReindex());
        } catch (Exception e) {
            log.error("图片 OCR 入库失败：documentId={}", document.getId(), e);
            updateStatus(document, KbDocument.STATUS_FAILED, null, trim(e.getMessage()));
        }
    }

    private List<TextSegment> parseAndSplit(KbDocument document) throws Exception {
        try (InputStream in = storageService.download(document.getObjectKey())) {
            return applyMetadata(documentParser.parse(in), document);
        }
    }

    /** 元数据会随分片一起写入 Qdrant，检索时用它过滤到具体知识库、文档与版本 */
    private List<TextSegment> applyMetadata(Document parsed, KbDocument document) {
        Metadata metadata = parsed.metadata();
        metadata.put("kbId", String.valueOf(document.getKbId()));
        metadata.put("docId", String.valueOf(document.getId()));
        metadata.put("fileName", document.getFileName());
        metadata.put("version", String.valueOf(document.getVersion()));
        // 只有最新版本参与检索，历史版本的分片会在生成新版本时被清除
        metadata.put("latest", String.valueOf(document.getLatest()));
        return splitIntoParentChild(parsed);
    }

    /**
     * 两级分片：先切出粒度较大的父块，再把每个父块切成子块。
     *
     * <p>子块是向量检索的单位——篇幅小、语义集中，相似度计算更准；
     * 父块正文随子块一起写入元数据，命中后作为 Prompt 输入——信息完整，上下文不被切断。
     * 子块与父块通过 parentIndex 关联，检索时同一父块下的多处命中只会喂给模型一次。
     */
    private List<TextSegment> splitIntoParentChild(Document parsed) {
        List<TextSegment> children = new ArrayList<>();
        int parentIndex = 0;
        for (TextSegment parent : parentDocumentSplitter.split(parsed)) {
            List<TextSegment> subChunks = documentSplitter.split(
                    Document.from(parent.text(), parent.metadata()));
            for (int childIndex = 0; childIndex < subChunks.size(); childIndex++) {
                Metadata metadata = new Metadata(subChunks.get(childIndex).metadata().toMap());
                metadata.put("parentIndex", String.valueOf(parentIndex));
                metadata.put("childIndex", String.valueOf(childIndex));
                metadata.put(METADATA_PARENT_TEXT, parent.text());
                metadata.put("vectorType", VECTOR_TYPE_CONTENT);
                children.add(TextSegment.from(subChunks.get(childIndex).text(), metadata));
            }
            parentIndex++;
        }
        log.info("两级分片完成：父块数={}, 子块数={}", parentIndex, children.size());
        return children;
    }

    /**
     * 为每个子块预生成“假设问题”，作为与正文并列的检索入口。
     * 问题条目复用子块的全部元数据（含 parentIndex 与父块正文），
     * 因此命中问题时喂给模型的仍然是它所属的父块。
     */
    private List<TextSegment> withHypotheticalQuestions(List<TextSegment> chunks) {
        RagProperties.HypotheticalQuestions config = properties.getIngest().getHypotheticalQuestions();
        if (!config.isEnabled() || config.getCount() <= 0) {
            return chunks;
        }
        List<TextSegment> segments = new ArrayList<>(chunks);
        for (TextSegment chunk : chunks) {
            for (String question : hypotheticalQuestionService.generate(chunk.text(), config.getCount())) {
                Metadata metadata = new Metadata(chunk.metadata().toMap());
                metadata.put("vectorType", VECTOR_TYPE_QUESTION);
                segments.add(TextSegment.from(question, metadata));
            }
        }
        log.info("假设问题生成完成：子块数={}, 向量条目数={}", chunks.size(), segments.size());
        return segments;
    }

    /** 向量化并写入 Qdrant，两条入库链路共用；chunkCount 为可检索的子块数量 */
    private void embedAndStore(KbDocument document, List<TextSegment> segments, int chunkCount, boolean reindex) {
        if (chunkCount == 0) {
            throw new IllegalStateException("未能从文档中解析出文本内容");
        }

        // 重新入库时先清掉该文档此前的向量（含假设问题），避免重复
        if (reindex) {
            embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("docId")
                    .isEqualTo(String.valueOf(document.getId())));
        }

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

        updateStatus(document, KbDocument.STATUS_DONE, chunkCount, null);
        log.info("文档入库完成：documentId={}, 子块数={}, 向量条目数={}",
                document.getId(), chunkCount, segments.size());
    }

    /** 按原件补齐内容摘要，失败不影响入库 */
    private void backfillContentHash(KbDocument document) {
        try (InputStream in = storageService.download(document.getObjectKey())) {
            KbDocument update = new KbDocument();
            update.setId(document.getId());
            update.setContentHash(ContentHash.of(in));
            documentMapper.updateById(update);
        } catch (Exception e) {
            log.warn("回填文档内容摘要失败：documentId={}, {}", document.getId(), e.getMessage());
        }
    }

    private void updateStatus(KbDocument document, int status, Integer chunkCount, String errorMsg) {
        KbDocument update = new KbDocument();
        update.setId(document.getId());
        update.setStatus(status);
        if (chunkCount != null) {
            update.setChunkCount(chunkCount);
        }
        update.setErrorMsg(errorMsg);
        documentMapper.updateById(update);
    }

    private String trim(String message) {
        if (message == null) {
            return "未知错误";
        }
        return message.length() > 480 ? message.substring(0, 480) : message;
    }
}

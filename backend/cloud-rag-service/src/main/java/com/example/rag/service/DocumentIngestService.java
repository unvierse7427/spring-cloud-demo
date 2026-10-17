package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.entity.KbDocument;
import com.example.rag.mapper.KbDocumentMapper;
import com.example.rag.mq.IngestMessage;
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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.List;

/**
 * 文档入库流水线：MinIO 取回原件 -> Tika 解析 -> 递归分片 -> 向量化 -> 写入 Qdrant。
 *
 * <p>由 RocketMQ 消费者触发，不阻塞上传接口。
 */
@Service
public class DocumentIngestService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestService.class);

    private final KbDocumentMapper documentMapper;

    private final StorageService storageService;

    private final DocumentParser documentParser;

    private final DocumentSplitter documentSplitter;

    private final EmbeddingModel embeddingModel;

    private final EmbeddingStore<TextSegment> embeddingStore;

    public DocumentIngestService(KbDocumentMapper documentMapper, StorageService storageService,
                                 DocumentParser documentParser, DocumentSplitter documentSplitter,
                                 EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.documentMapper = documentMapper;
        this.storageService = storageService;
        this.documentParser = documentParser;
        this.documentSplitter = documentSplitter;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
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
        try {
            List<TextSegment> segments = parseAndSplit(document);
            if (segments.isEmpty()) {
                throw new IllegalStateException("未能从文档中解析出文本内容");
            }

            // 重新入库时先清掉该文档此前的向量，避免重复
            if (message.isReindex()) {
                embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("docId")
                        .isEqualTo(String.valueOf(document.getId())));
            }

            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);

            updateStatus(document, KbDocument.STATUS_DONE, segments.size(), null);
            log.info("文档入库完成：documentId={}, 分片数={}", document.getId(), segments.size());
        } catch (Exception e) {
            log.error("文档入库失败：documentId={}", document.getId(), e);
            updateStatus(document, KbDocument.STATUS_FAILED, null, trim(e.getMessage()));
        }
    }

    private List<TextSegment> parseAndSplit(KbDocument document) throws Exception {
        try (InputStream in = storageService.download(document.getObjectKey())) {
            Document parsed = documentParser.parse(in);
            Metadata metadata = parsed.metadata();
            // 元数据会随分片一起写入 Qdrant，检索时用它过滤到具体知识库、文档与版本
            metadata.put("kbId", String.valueOf(document.getKbId()));
            metadata.put("docId", String.valueOf(document.getId()));
            metadata.put("fileName", document.getFileName());
            metadata.put("version", String.valueOf(document.getVersion()));
            // 只有最新版本参与检索，历史版本的分片会在生成新版本时被清除
            metadata.put("latest", String.valueOf(document.getLatest()));
            return documentSplitter.split(parsed);
        }
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

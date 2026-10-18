package com.example.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.config.RagProperties;
import com.example.rag.dto.CreateKnowledgeBaseRequest;
import com.example.rag.dto.DocumentPreview;
import com.example.rag.dto.DocumentView;
import com.example.rag.dto.KnowledgeBaseView;
import com.example.rag.dto.RoleOption;
import com.example.rag.dto.UploadResult;
import com.example.rag.entity.KbDocument;
import com.example.rag.entity.KnowledgeBase;
import com.example.rag.mapper.KbDocumentMapper;
import com.example.rag.mapper.KnowledgeBaseMapper;
import com.example.rag.mapper.SysRoleMapper;
import com.example.rag.mq.DocumentIngestProducer;
import com.example.rag.mq.IngestMessage;
import com.example.rag.support.ContentHash;
import com.example.rag.support.CurrentUser;
import com.example.rag.support.DocumentPermission;
import com.example.rag.support.QaHistoryStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库业务：知识库增删查、文档上传（MinIO + RocketMQ 异步向量化）、文档状态查询
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private static final int MIN_NAME_LENGTH = 2;

    private static final int MAX_NAME_LENGTH = 32;

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    private final KbDocumentMapper documentMapper;

    private final StorageService storageService;

    private final DocumentParser documentParser;

    private final DocumentIngestProducer ingestProducer;

    private final EmbeddingStore<TextSegment> embeddingStore;

    private final RagProperties properties;

    private final QaHistoryStore historyStore;

    private final SysRoleMapper sysRoleMapper;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper, KbDocumentMapper documentMapper,
                               StorageService storageService, DocumentParser documentParser,
                               DocumentIngestProducer ingestProducer,
                               EmbeddingStore<TextSegment> embeddingStore, RagProperties properties,
                               QaHistoryStore historyStore, SysRoleMapper sysRoleMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.storageService = storageService;
        this.documentParser = documentParser;
        this.ingestProducer = ingestProducer;
        this.embeddingStore = embeddingStore;
        this.properties = properties;
        this.historyStore = historyStore;
        this.sysRoleMapper = sysRoleMapper;
    }

    /** 可供文档选择的可见角色列表（取自系统角色表） */
    public List<RoleOption> listRoleOptions() {
        return sysRoleMapper.listOptions();
    }

    /** 知识库列表，附带各自的文档数量 */
    public List<KnowledgeBaseView> list() {
        List<KnowledgeBase> bases = knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>().orderByDesc(KnowledgeBase::getId));
        if (bases.isEmpty()) {
            return List.of();
        }

        List<Long> kbIds = bases.stream().map(KnowledgeBase::getId).toList();
        Collection<String> authorities = CurrentUser.authorities();
        Map<Long, Long> counts = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                        .in(KbDocument::getKbId, kbIds)
                        .select(KbDocument::getId, KbDocument::getKbId, KbDocument::getVisibleRoles)).stream()
                .filter(document -> DocumentPermission.canView(document.getVisibleRoles(), authorities))
                .collect(Collectors.groupingBy(KbDocument::getKbId, Collectors.counting()));

        List<KnowledgeBaseView> views = new ArrayList<>(bases.size());
        for (KnowledgeBase base : bases) {
            views.add(toView(base, counts.getOrDefault(base.getId(), 0L)));
        }
        return views;
    }

    public KnowledgeBase create(CreateKnowledgeBaseRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }
        String name = request.getName().trim();
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("知识库名称长度需在 " + MIN_NAME_LENGTH + "-" + MAX_NAME_LENGTH + " 个字符之间");
        }
        KnowledgeBase existing = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getName, name)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new IllegalArgumentException("知识库名称已存在：" + name);
        }

        KnowledgeBase base = new KnowledgeBase();
        base.setName(name);
        base.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        base.setEmbeddingModel(properties.getDashscope().getEmbeddingModel());
        base.setOwnerId(CurrentUser.id());
        base.setOwnerName(CurrentUser.name());
        knowledgeBaseMapper.insert(base);
        return knowledgeBaseMapper.selectById(base.getId());
    }

    /** 知识库详情，不存在时抛出业务异常 */
    public KnowledgeBase getRequired(Long kbId) {
        KnowledgeBase base = kbId == null ? null : knowledgeBaseMapper.selectById(kbId);
        if (base == null) {
            throw new IllegalArgumentException("知识库不存在：" + kbId);
        }
        return base;
    }

    /** 删除知识库：连同其文档、MinIO 原件与向量一并清理，仅创建者可操作 */
    public void delete(Long kbId) {
        KnowledgeBase base = getRequired(kbId);
        if (!base.getOwnerId().equals(CurrentUser.id())) {
            throw new IllegalArgumentException("只能删除自己创建的知识库");
        }

        List<KbDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getKbId, kbId));
        for (KbDocument document : documents) {
            cleanupDocument(document);
        }
        embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("kbId").isEqualTo(String.valueOf(kbId)));
        knowledgeBaseMapper.deleteById(kbId);
        historyStore.clearAll(kbId);
        log.info("已删除知识库：kbId={}, 文档数={}", kbId, documents.size());
    }

    /**
     * 批量上传文档：原件存入 MinIO，随后投递消息异步解析与向量化。
     * roles 为文档的可见角色码，为空表示所有用户可见。
     */
    public List<UploadResult> uploadDocuments(Long kbId, List<MultipartFile> files, List<String> roles) {
        getRequired(kbId);
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String visibleRoles = DocumentPermission.join(roles);
        List<UploadResult> results = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            results.add(uploadOne(kbId, file, visibleRoles));
        }
        return results;
    }

    /**
     * 单个文件的入库处理，按“同一知识库内同名文件视为同一文档”的规则维护版本号：
     * 首次上传入库为 v1；再次上传时若内容与最新版本完全一致则不入库，仅提示库中已有；
     * 内容有变化则入库为新版本，旧版本降级为历史版本（原件与记录保留，但不再参与检索）。
     */
    private UploadResult uploadOne(Long kbId, MultipartFile file, String visibleRoles) {
        if (file == null || file.isEmpty()) {
            return UploadResult.failed("未命名文件", "上传文件为空");
        }
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim() : "未命名文件";

        // 图片会走 OCR 识别，体积上限比普通文档更严（与 Python 侧 OCR_MAX_IMAGE_MB 一致）
        RagProperties.Ocr ocr = properties.getOcr();
        if (ocr.isImage(fileName) && file.getSize() > ocr.getMaxImageBytes()) {
            return UploadResult.failed(fileName, String.format("图片体积 %.2fMB 超出上限 %.0fMB",
                    file.getSize() / 1024.0 / 1024.0, ocr.getMaxImageMb()));
        }

        String contentHash;
        try (InputStream in = file.getInputStream()) {
            contentHash = ContentHash.of(in);
        } catch (Exception e) {
            log.error("读取上传文件失败：fileName={}", fileName, e);
            return UploadResult.failed(fileName, "读取上传文件失败：" + e.getMessage());
        }

        KbDocument previous = findLatest(kbId, fileName);
        if (previous != null && contentHash.equals(contentHashOf(previous))) {
            return UploadResult.duplicate(fileName, previous.getId(), previous.getVersion());
        }
        int version = previous == null ? 1 : previous.getVersion() + 1;

        String objectKey = kbId + "/" + UUID.randomUUID() + "-" + fileName;
        try (InputStream in = file.getInputStream()) {
            storageService.upload(objectKey, in, file.getSize(), file.getContentType());
        } catch (Exception e) {
            log.error("文档上传 MinIO 失败：fileName={}", fileName, e);
            return UploadResult.failed(fileName, "文档上传失败：" + e.getMessage());
        }

        if (previous != null) {
            supersede(previous, version);
        }

        KbDocument document = new KbDocument();
        document.setKbId(kbId);
        document.setDocKey(fileName);
        document.setVersion(version);
        document.setContentHash(contentHash);
        document.setLatest(KbDocument.LATEST_YES);
        document.setVisibleRoles(visibleRoles);
        document.setFileName(fileName);
        document.setObjectKey(objectKey);
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setStatus(KbDocument.STATUS_PENDING);
        document.setChunkCount(0);
        document.setOwnerId(CurrentUser.id());
        documentMapper.insert(document);

        ingestProducer.send(new IngestMessage(document.getId(), kbId, objectKey, true));
        return previous == null
                ? UploadResult.added(fileName, document.getId(), version)
                : UploadResult.updated(fileName, document.getId(), version, previous.getVersion());
    }

    /** 同一知识库内同名文档的最新版本 */
    private KbDocument findLatest(Long kbId, String docKey) {
        return documentMapper.selectOne(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getKbId, kbId)
                .eq(KbDocument::getDocKey, docKey)
                .eq(KbDocument::getLatest, KbDocument.LATEST_YES)
                .orderByDesc(KbDocument::getVersion)
                .last("LIMIT 1"));
    }

    /** 取入库时记录的内容摘要；早期数据没有摘要时按原件即时计算并回填 */
    private String contentHashOf(KbDocument document) {
        if (StringUtils.hasText(document.getContentHash())) {
            return document.getContentHash();
        }
        try (InputStream in = storageService.download(document.getObjectKey())) {
            String hash = ContentHash.of(in);
            KbDocument update = new KbDocument();
            update.setId(document.getId());
            update.setContentHash(hash);
            documentMapper.updateById(update);
            return hash;
        } catch (Exception e) {
            log.warn("计算文档内容摘要失败：documentId={}, {}", document.getId(), e.getMessage());
            return null;
        }
    }

    /** 旧版本降级为历史版本：原件与记录保留，仅清除其向量，避免参与检索 */
    private void supersede(KbDocument previous, int newVersion) {
        KbDocument update = new KbDocument();
        update.setId(previous.getId());
        update.setLatest(KbDocument.LATEST_NO);
        documentMapper.updateById(update);
        embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("docId")
                .isEqualTo(String.valueOf(previous.getId())));
        log.info("文档产生新版本：docKey={}, v{} -> v{}", previous.getDocKey(), previous.getVersion(), newVersion);
    }

    /** 知识库下的文档列表，只返回当前用户有权限查看的文档 */
    public List<DocumentView> listDocuments(Long kbId) {
        getRequired(kbId);
        Collection<String> authorities = CurrentUser.authorities();
        return documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                        .eq(KbDocument::getKbId, kbId)
                        .orderByDesc(KbDocument::getLatest)
                        .orderByDesc(KbDocument::getVersion)
                        .orderByDesc(KbDocument::getId)).stream()
                .filter(document -> DocumentPermission.canView(document.getVisibleRoles(), authorities))
                .map(this::toView)
                .collect(Collectors.toList());
    }

    /**
     * 从候选文档 id 中筛出当前用户有权查看的部分，供问答检索时过滤引用来源。
     */
    public Set<Long> visibleDocumentIds(Collection<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Set.of();
        }
        Collection<String> authorities = CurrentUser.authorities();
        return documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                        .in(KbDocument::getId, documentIds)
                        .select(KbDocument::getId, KbDocument::getVisibleRoles)).stream()
                .filter(document -> DocumentPermission.canView(document.getVisibleRoles(), authorities))
                .map(KbDocument::getId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /** 重新入库：重置状态后再次投递 MQ，消费者会先清掉旧向量 */
    public void reindex(Long documentId) {
        KbDocument document = getRequiredDocument(documentId);
        KbDocument update = new KbDocument();
        update.setId(document.getId());
        update.setStatus(KbDocument.STATUS_PENDING);
        update.setChunkCount(0);
        documentMapper.updateById(update);
        ingestProducer.send(new IngestMessage(document.getId(), document.getKbId(), document.getObjectKey(), true));
    }

    /** 删除文档：清理向量、MinIO 原件与数据库记录；删除的是最新版本时回退到上一个历史版本 */
    public void deleteDocument(Long documentId) {
        KbDocument document = getRequiredDocument(documentId);
        cleanupDocument(document);
        if (document.getLatest() != null && document.getLatest() == KbDocument.LATEST_YES) {
            promotePrevious(document);
        }
    }

    /** 最新版本被删除后，把剩余的最高版本重新置为最新版本并重新向量化 */
    private void promotePrevious(KbDocument removed) {
        KbDocument previous = documentMapper.selectOne(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getKbId, removed.getKbId())
                .eq(KbDocument::getDocKey, removed.getDocKey())
                .orderByDesc(KbDocument::getVersion)
                .last("LIMIT 1"));
        if (previous == null) {
            return;
        }
        KbDocument update = new KbDocument();
        update.setId(previous.getId());
        update.setLatest(KbDocument.LATEST_YES);
        update.setStatus(KbDocument.STATUS_PENDING);
        update.setChunkCount(0);
        documentMapper.updateById(update);
        ingestProducer.send(new IngestMessage(previous.getId(), previous.getKbId(), previous.getObjectKey(), true));
        log.info("最新版本已删除，回退历史版本：docKey={}, v{}", previous.getDocKey(), previous.getVersion());
    }

    /**
     * 生成文档原件的临时访问地址。
     * download 为 false 时返回内联地址供浏览器预览，为 true 时返回附件地址直接触发下载。
     */
    public DocumentPreview preview(Long documentId, boolean download) {
        KbDocument document = getRequiredDocument(documentId);
        if (!DocumentPermission.canView(document.getVisibleRoles(), CurrentUser.authorities())) {
            throw new IllegalArgumentException("无权访问该文档");
        }
        DocumentPreview preview = new DocumentPreview();
        preview.setDocumentId(document.getId());
        preview.setFileName(document.getFileName());
        preview.setContentType(document.getContentType());
        preview.setFileSize(document.getFileSize());
        preview.setUrl(storageService.presignedUrl(document.getObjectKey(), document.getFileName(),
                document.getContentType(), download));
        if (!download && isLegacyWord(document)) {
            preview.setParsedText(parsePreviewText(document));
        }
        return preview;
    }

    /** 旧版 .doc 浏览器内兼容性较差，改为解析文本后在前端展示 */
    private boolean isLegacyWord(KbDocument document) {
        String fileName = document.getFileName();
        return StringUtils.hasText(fileName) && fileName.toLowerCase().endsWith(".doc")
                && !fileName.toLowerCase().endsWith(".docx");
    }

    private String parsePreviewText(KbDocument document) {
        try (InputStream in = storageService.download(document.getObjectKey())) {
            Document parsed = documentParser.parse(in);
            return StringUtils.hasText(parsed.text()) ? parsed.text() : "文档内容为空";
        } catch (Exception e) {
            log.warn("解析文档预览文本失败：documentId={}, {}", document.getId(), e.getMessage());
            return "暂时无法解析该文档内容，请下载原件后查看。";
        }
    }

    private KbDocument getRequiredDocument(Long documentId) {
        KbDocument document = documentId == null ? null : documentMapper.selectById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在：" + documentId);
        }
        return document;
    }

    private void cleanupDocument(KbDocument document) {
        embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("docId")
                .isEqualTo(String.valueOf(document.getId())));
        storageService.remove(document.getObjectKey());
        documentMapper.deleteById(document.getId());
    }

    private KnowledgeBaseView toView(KnowledgeBase base, Long documentCount) {
        KnowledgeBaseView view = new KnowledgeBaseView();
        view.setId(base.getId());
        view.setName(base.getName());
        view.setDescription(base.getDescription());
        view.setEmbeddingModel(base.getEmbeddingModel());
        view.setOwnerName(base.getOwnerName());
        view.setDocumentCount(documentCount);
        view.setCreateTime(base.getCreateTime());
        return view;
    }

    private DocumentView toView(KbDocument document) {
        DocumentView view = new DocumentView();
        view.setId(document.getId());
        view.setKbId(document.getKbId());
        view.setFileName(document.getFileName());
        view.setVersion(document.getVersion());
        view.setLatest(document.getLatest());
        view.setVisibleRoles(document.getVisibleRoles());
        view.setContentType(document.getContentType());
        view.setFileSize(document.getFileSize());
        view.setStatus(document.getStatus());
        view.setStatusText(statusText(document.getStatus()));
        view.setChunkCount(document.getChunkCount());
        view.setErrorMsg(document.getErrorMsg());
        view.setCreateTime(document.getCreateTime());
        return view;
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case KbDocument.STATUS_PENDING -> "待处理";
            case KbDocument.STATUS_PROCESSING -> "处理中";
            case KbDocument.STATUS_DONE -> "已完成";
            case KbDocument.STATUS_FAILED -> "处理失败";
            default -> "未知";
        };
    }
}

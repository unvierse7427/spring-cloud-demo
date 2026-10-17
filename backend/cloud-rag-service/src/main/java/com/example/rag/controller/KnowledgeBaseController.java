package com.example.rag.controller;

import com.example.rag.dto.AskRequest;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.CreateKnowledgeBaseRequest;
import com.example.rag.dto.DocumentPreview;
import com.example.rag.dto.DocumentView;
import com.example.rag.dto.KnowledgeBaseView;
import com.example.rag.dto.QaHistoryEntry;
import com.example.rag.dto.RoleOption;
import com.example.rag.dto.UploadResult;
import com.example.rag.entity.KnowledgeBase;
import com.example.rag.service.KnowledgeBaseService;
import com.example.rag.service.RagQueryService;
import com.example.rag.support.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库接口
 *
 * <p>对外访问路径：网关 /api/rag/** -> 本服务 /**，因此这里以 /kb 作为业务前缀。
 */
@RestController
@RequestMapping("/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    private final RagQueryService ragQueryService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService, RagQueryService ragQueryService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.ragQueryService = ragQueryService;
    }

    /** 知识库列表 */
    @GetMapping
    public Result<List<KnowledgeBaseView>> list() {
        return Result.success(knowledgeBaseService.list());
    }

    /** 新建知识库 */
    @PostMapping
    public Result<KnowledgeBase> create(@RequestBody CreateKnowledgeBaseRequest request) {
        return Result.success("知识库创建成功", knowledgeBaseService.create(request));
    }

    /** 删除知识库（连同文档与向量） */
    @DeleteMapping("/{kbId}")
    public Result<Void> delete(@PathVariable Long kbId) {
        knowledgeBaseService.delete(kbId);
        return Result.success("知识库已删除", null);
    }

    /** 知识库下的文档列表 */
    @GetMapping("/{kbId}/documents")
    public Result<List<DocumentView>> documents(@PathVariable Long kbId) {
        return Result.success(knowledgeBaseService.listDocuments(kbId));
    }

    /** 可供文档选择的可见角色列表（取自系统角色表） */
    @GetMapping("/roles")
    public Result<List<RoleOption>> roleOptions() {
        return Result.success(knowledgeBaseService.listRoleOptions());
    }

    /**
     * 批量上传文档，逐个返回处理结果（新增 / 新版本 / 库中已有 / 失败）。
     *
     * <p>同名文件的首次上传入库为 v1，内容一致时提示库中已有而不再入库，
     * 内容有变化则入库为新版本并把旧版本保留为历史版本。向量化在后台异步完成。
     *
     * <p>roles 为文档的可见角色码，不传或为空表示所有登录用户可见；
     * 非空时只有拥有其中任一角色的用户才能查看该文档并使用其内容问答。
     */
    @PostMapping("/{kbId}/documents")
    public Result<List<UploadResult>> upload(@PathVariable Long kbId,
                                             @RequestParam("files") List<MultipartFile> files,
                                             @RequestParam(value = "roles", required = false) List<String> roles) {
        return Result.success("上传处理完成", knowledgeBaseService.uploadDocuments(kbId, files, roles));
    }

    /** 重新解析并向量化指定文档 */
    @PostMapping("/documents/{documentId}/reindex")
    public Result<Void> reindex(@PathVariable Long documentId) {
        knowledgeBaseService.reindex(documentId);
        return Result.success("已重新提交入库任务", null);
    }

    /** 删除文档（连同向量与原件） */
    @DeleteMapping("/documents/{documentId}")
    public Result<Void> deleteDocument(@PathVariable Long documentId) {
        knowledgeBaseService.deleteDocument(documentId);
        return Result.success("文档已删除", null);
    }

    /** 生成文档原件的临时预览地址（内联打开），供前端在界面内预览 */
    @GetMapping("/documents/{documentId}/preview")
    public Result<DocumentPreview> preview(@PathVariable Long documentId) {
        return Result.success(knowledgeBaseService.preview(documentId, false));
    }

    /** 生成文档原件的临时下载地址（附件形式），浏览器会直接保存文件 */
    @GetMapping("/documents/{documentId}/download")
    public Result<DocumentPreview> download(@PathVariable Long documentId) {
        return Result.success(knowledgeBaseService.preview(documentId, true));
    }

    /** 知识库问答：检索 + 大模型作答 + 引用来源 */
    @PostMapping("/{kbId}/ask")
    public Result<AskResponse> ask(@PathVariable Long kbId, @RequestBody AskRequest request) {
        return Result.success(ragQueryService.ask(kbId, request));
    }

    /** 当前用户在该知识库下的智能问答历史，最新在前 */
    @GetMapping("/{kbId}/history")
    public Result<List<QaHistoryEntry>> history(@PathVariable Long kbId) {
        return Result.success(ragQueryService.history(kbId));
    }

    /** 清空当前用户在该知识库下的智能问答历史 */
    @DeleteMapping("/{kbId}/history")
    public Result<Void> clearHistory(@PathVariable Long kbId) {
        ragQueryService.clearHistory(kbId);
        return Result.success("问答历史已清空", null);
    }
}

package com.example.rag.dto;

/**
 * 批量上传时单个文件的处理结果
 */
public class UploadResult {

    /** 新增：知识库中原本没有同名文档，本次入库为 v1 */
    public static final String NEW = "NEW";

    /** 新版本：同名文档内容有变化，已入库为新版本，旧版本保留为历史 */
    public static final String UPDATED = "UPDATED";

    /** 重复：同名文档的最新版本内容与本次完全一致，未重复入库 */
    public static final String DUPLICATE = "DUPLICATE";

    /** 失败：文件为空或存取过程中出错 */
    public static final String FAILED = "FAILED";

    private String fileName;

    /** NEW / UPDATED / DUPLICATE / FAILED */
    private String result;

    /** 直接展示给用户的说明 */
    private String message;

    /** 本次涉及的最新版本文档 id，重复时为已存在的那条记录 id */
    private Long documentId;

    /** 本次上传对应的版本号 */
    private Integer version;

    public static UploadResult added(String fileName, Long documentId, int version) {
        UploadResult result = new UploadResult();
        result.setFileName(fileName);
        result.setResult(NEW);
        result.setMessage("上传成功，已入库为 v" + version + "，正在后台解析向量化");
        result.setDocumentId(documentId);
        result.setVersion(version);
        return result;
    }

    public static UploadResult updated(String fileName, Long documentId, int version, int previousVersion) {
        UploadResult result = new UploadResult();
        result.setFileName(fileName);
        result.setResult(UPDATED);
        result.setMessage("检测到内容有变化，已入库为 v" + version + "，v" + previousVersion + " 保留为历史版本");
        result.setDocumentId(documentId);
        result.setVersion(version);
        return result;
    }

    public static UploadResult duplicate(String fileName, Long documentId, int version) {
        UploadResult result = new UploadResult();
        result.setFileName(fileName);
        result.setResult(DUPLICATE);
        result.setMessage("知识库中已有内容完全一致的文档（v" + version + "），本次未重复入库");
        result.setDocumentId(documentId);
        result.setVersion(version);
        return result;
    }

    public static UploadResult failed(String fileName, String message) {
        UploadResult result = new UploadResult();
        result.setFileName(fileName);
        result.setResult(FAILED);
        result.setMessage(message);
        return result;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}

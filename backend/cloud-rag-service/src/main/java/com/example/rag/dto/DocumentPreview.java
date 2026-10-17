package com.example.rag.dto;

/**
 * 文档预览/下载信息：携带一个可直接在浏览器打开的临时地址
 */
public class DocumentPreview {

    private Long documentId;

    private String fileName;

    private String contentType;

    private Long fileSize;

    /** MinIO 预签名地址，有效期 10 分钟；预览接口为内联打开，下载接口为附件保存 */
    private String url;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

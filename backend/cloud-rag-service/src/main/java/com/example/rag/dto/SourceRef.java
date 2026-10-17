package com.example.rag.dto;

/**
 * 回答引用的资料片段
 */
public class SourceRef {

    private Long documentId;

    private String fileName;

    /** 命中的文档版本号，检索只使用最新版本 */
    private Integer version;

    /** 向量相似度得分 */
    private Double score;

    /** 命中的原文片段 */
    private String snippet;

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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
}

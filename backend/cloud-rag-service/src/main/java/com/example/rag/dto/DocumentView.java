package com.example.rag.dto;

import java.time.LocalDateTime;

/**
 * 文档列表项
 */
public class DocumentView {

    private Long id;

    private Long kbId;

    private String fileName;

    /** 版本号，同名文档首次上传为 1，内容变化后递增 */
    private Integer version;

    /** 是否最新版本：1 是 0 否（0 表示历史版本，保留存档但不参与检索） */
    private Integer latest;

    /** 可见角色码，逗号分隔，为空表示所有用户可见 */
    private String visibleRoles;

    private String contentType;

    private Long fileSize;

    private Integer status;

    /** 状态中文说明，前端直接展示 */
    private String statusText;

    private Integer chunkCount;

    private String errorMsg;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
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

    public Integer getLatest() {
        return latest;
    }

    public void setLatest(Integer latest) {
        this.latest = latest;
    }

    public String getVisibleRoles() {
        return visibleRoles;
    }

    public void setVisibleRoles(String visibleRoles) {
        this.visibleRoles = visibleRoles;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}

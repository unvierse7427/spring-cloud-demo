package com.example.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档实体，对应表 kb_document
 */
@TableName("kb_document")
public class KbDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待处理 */
    public static final int STATUS_PENDING = 0;

    /** 处理中 */
    public static final int STATUS_PROCESSING = 1;

    /** 已完成 */
    public static final int STATUS_DONE = 2;

    /** 处理失败 */
    public static final int STATUS_FAILED = 3;

    /** 最新版本，参与检索 */
    public static final int LATEST_YES = 1;

    /** 历史版本，仅作为存档保留，不参与检索 */
    public static final int LATEST_NO = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    /** 原始文件名 */
    private String fileName;

    /** 逻辑文档标识：同一知识库内同名文件视为同一文档的不同版本 */
    private String docKey;

    /** 版本号，同名文档首次上传为 1，内容变化后递增 */
    private Integer version;

    /** 文件内容 SHA-256，用于比对两次上传的内容是否一致 */
    private String contentHash;

    /** 是否最新版本：1 是 0 否 */
    private Integer latest;

    /** 可见角色码，逗号分隔（如 ROLE_ADMIN,ROLE_USER），为空表示所有用户可见 */
    private String visibleRoles;

    /** 在 MinIO 中的对象 key */
    private String objectKey;

    private String contentType;

    private Long fileSize;

    /** 入库状态：0 待处理 1 处理中 2 已完成 3 失败 */
    private Integer status;

    /** 切分出的分片数 */
    private Integer chunkCount;

    private String errorMsg;

    private Long ownerId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

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

    public String getDocKey() {
        return docKey;
    }

    public void setDocKey(String docKey) {
        this.docKey = docKey;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
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

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
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

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}

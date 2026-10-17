package com.example.rag.mq;

import java.io.Serializable;

/**
 * 文档入库消息：MinIO 上传完成后投递到 RocketMQ，由消费者异步解析与向量化
 */
public class IngestMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long documentId;

    private Long kbId;

    private String objectKey;

    /** 重新入库时先清理该文档已有的向量 */
    private boolean reindex;

    public IngestMessage() {
    }

    public IngestMessage(Long documentId, Long kbId, String objectKey, boolean reindex) {
        this.documentId = documentId;
        this.kbId = kbId;
        this.objectKey = objectKey;
        this.reindex = reindex;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public boolean isReindex() {
        return reindex;
    }

    public void setReindex(boolean reindex) {
        this.reindex = reindex;
    }
}

package com.example.rag.mq;

import java.io.Serializable;

/**
 * 图片 OCR 请求消息：入库时发现是图片，无法用 Tika 解析文本，
 * 于是投递该消息，由消费者调用 Python OCR 服务识别后再走分片与向量化。
 */
public class OcrRequestMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long documentId;

    private Long kbId;

    private String objectKey;

    private String fileName;

    /** 重新入库时先清理该文档已有的向量 */
    private boolean reindex;

    public OcrRequestMessage() {
    }

    public OcrRequestMessage(Long documentId, Long kbId, String objectKey, String fileName, boolean reindex) {
        this.documentId = documentId;
        this.kbId = kbId;
        this.objectKey = objectKey;
        this.fileName = fileName;
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isReindex() {
        return reindex;
    }

    public void setReindex(boolean reindex) {
        this.reindex = reindex;
    }

    @Override
    public String toString() {
        return "OcrRequestMessage{documentId=" + documentId + ", kbId=" + kbId
                + ", fileName='" + fileName + "', reindex=" + reindex + '}';
    }
}

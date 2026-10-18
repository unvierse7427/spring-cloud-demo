package com.example.rag.mq;

import com.example.rag.service.DocumentIngestService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 图片 OCR 请求消费者：调用 Python OCR 服务识别文字，
 * 再复用文档入库流水线完成分片、向量化并写入 Qdrant
 */
@Component
@RocketMQMessageListener(
        topic = "${cloud.rag.ocr.topic}",
        consumerGroup = "${cloud.rag.ocr.consumer-group}")
public class OcrRequestConsumer implements RocketMQListener<OcrRequestMessage> {

    private static final Logger log = LoggerFactory.getLogger(OcrRequestConsumer.class);

    private final DocumentIngestService ingestService;

    public OcrRequestConsumer(DocumentIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Override
    public void onMessage(OcrRequestMessage message) {
        log.info("收到图片 OCR 请求：{}", message);
        ingestService.ingestByOcr(message);
    }
}

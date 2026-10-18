package com.example.rag.mq;

import com.example.rag.config.RagProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 图片 OCR 请求生产者：入库时识别到图片即投递消息，
 * 由消费者异步调用 Python OCR 服务，避免阻塞上传接口
 */
@Component
public class OcrRequestProducer {

    private static final Logger log = LoggerFactory.getLogger(OcrRequestProducer.class);

    private final RocketMQTemplate rocketMQTemplate;

    private final RagProperties properties;

    public OcrRequestProducer(RocketMQTemplate rocketMQTemplate, RagProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    public void send(OcrRequestMessage message) {
        rocketMQTemplate.convertAndSend(properties.getOcr().getTopic(), message);
        log.info("已投递图片 OCR 请求：documentId={}, fileName={}", message.getDocumentId(), message.getFileName());
    }
}

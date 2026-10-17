package com.example.rag.mq;

import com.example.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

/**
 * 文档入库消息生产者：让上传接口快速返回，解析与向量化交给消费者异步完成
 */
@Component
public class DocumentIngestProducer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestProducer.class);

    private final RocketMQTemplate rocketMQTemplate;

    private final RagProperties properties;

    public DocumentIngestProducer(RocketMQTemplate rocketMQTemplate, RagProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    public void send(IngestMessage message) {
        rocketMQTemplate.convertAndSend(properties.getIngest().getTopic(), message);
        log.info("已投递文档入库消息：documentId={}, reindex={}", message.getDocumentId(), message.isReindex());
    }
}

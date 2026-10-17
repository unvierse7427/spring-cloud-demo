package com.example.rag.mq;

import com.example.rag.service.DocumentIngestService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 文档入库消息消费者：真正执行 Tika 解析、分片、向量化并写入 Qdrant
 */
@Component
@RocketMQMessageListener(
        topic = "${cloud.rag.ingest.topic}",
        consumerGroup = "${cloud.rag.ingest.consumer-group}")
public class DocumentIngestConsumer implements RocketMQListener<IngestMessage> {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestConsumer.class);

    private final DocumentIngestService ingestService;

    public DocumentIngestConsumer(DocumentIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Override
    public void onMessage(IngestMessage message) {
        log.info("收到文档入库消息：{}", message);
        ingestService.ingest(message);
    }
}

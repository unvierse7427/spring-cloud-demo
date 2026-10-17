package com.example.rag.config;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 组件装配：嵌入模型、对话模型、向量库、文档解析与分片器。
 *
 * <p>Embedding 固定使用通义千问 DashScope（DeepSeek 未提供 embeddings 接口）；
 * 对话模型默认同样使用通义千问，可通过 cloud.rag.llm.provider=deepseek 切换。
 */
@Configuration
public class LangChain4jConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jConfig.class);

    /** 通义千问嵌入模型，默认 text-embedding-v2 */
    @Bean
    public EmbeddingModel embeddingModel(RagProperties properties) {
        RagProperties.Dashscope dashscope = properties.getDashscope();
        if (dashscope.getApiKey() == null || dashscope.getApiKey().isBlank()) {
            throw new IllegalStateException("缺少通义千问 API Key，请配置环境变量 DASHSCOPE_API_KEY");
        }
        return QwenEmbeddingModel.builder()
                .apiKey(dashscope.getApiKey())
                .modelName(dashscope.getEmbeddingModel())
                .build();
    }

    /** 默认对话模型：通义千问 */
    @Bean
    @ConditionalOnProperty(name = "cloud.rag.llm.provider", havingValue = "qwen", matchIfMissing = true)
    public ChatModel qwenChatModel(RagProperties properties) {
        RagProperties.Dashscope dashscope = properties.getDashscope();
        if (dashscope.getApiKey() == null || dashscope.getApiKey().isBlank()) {
            throw new IllegalStateException("缺少通义千问 API Key，请配置环境变量 DASHSCOPE_API_KEY");
        }
        return QwenChatModel.builder()
                .apiKey(dashscope.getApiKey())
                .modelName(dashscope.getChatModel())
                .build();
    }

    /** 可选对话模型：DeepSeek（OpenAI 兼容协议），设置 cloud.rag.llm.provider=deepseek 生效 */
    @Bean
    @ConditionalOnProperty(name = "cloud.rag.llm.provider", havingValue = "deepseek")
    public ChatModel deepSeekChatModel(RagProperties properties) {
        RagProperties.Deepseek deepseek = properties.getLlm().getDeepseek();
        if (deepseek.getApiKey() == null || deepseek.getApiKey().isBlank()) {
            throw new IllegalStateException("缺少 DeepSeek API Key，请配置环境变量 DEEPSEEK_API_KEY");
        }
        return OpenAiChatModel.builder()
                .baseUrl(deepseek.getBaseUrl())
                .apiKey(deepseek.getApiKey())
                .modelName(deepseek.getModelName())
                .build();
    }

    /** 向量库：Qdrant，使用 gRPC 端口；集合缺失时按嵌入模型实际维度自动创建 */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(RagProperties properties, EmbeddingModel embeddingModel) throws Exception {
        RagProperties.Qdrant qdrant = properties.getQdrant();
        try (QdrantClient client = new QdrantClient(
                QdrantGrpcClient.newBuilder(qdrant.getHost(), qdrant.getPort(), false).build())) {
            if (!client.collectionExistsAsync(qdrant.getCollection()).get()) {
                int dimension = embeddingModel.embed("维度探测").content().dimension();
                client.createCollectionAsync(qdrant.getCollection(), VectorParams.newBuilder()
                        .setDistance(Distance.Cosine)
                        .setSize(dimension)
                        .build()).get();
                log.info("已创建 Qdrant 集合 {}，向量维度 {}", qdrant.getCollection(), dimension);
            }
        }
        return QdrantEmbeddingStore.builder()
                .host(qdrant.getHost())
                .port(qdrant.getPort())
                .collectionName(qdrant.getCollection())
                .build();
    }

    /** 文档解析：Apache Tika，可覆盖 pdf、word、excel、ppt、html、txt 等格式 */
    @Bean
    public DocumentParser documentParser() {
        return new ApacheTikaDocumentParser();
    }

    /** 递归分片器：优先按段落、换行切分，保留相邻分片的重叠内容 */
    @Bean
    public DocumentSplitter documentSplitter(RagProperties properties) {
        return DocumentSplitters.recursive(properties.getIngest().getChunkSize(), properties.getIngest().getChunkOverlap());
    }
}

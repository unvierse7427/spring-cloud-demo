package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 相关配置，对应 application.yml 中的 cloud.rag 前缀
 */
@ConfigurationProperties(prefix = "cloud.rag")
public class RagProperties {

    private final Dashscope dashscope = new Dashscope();

    private final Llm llm = new Llm();

    private final Qdrant qdrant = new Qdrant();

    private final Minio minio = new Minio();

    private final Ingest ingest = new Ingest();

    private final Query query = new Query();

    public Dashscope getDashscope() {
        return dashscope;
    }

    public Llm getLlm() {
        return llm;
    }

    public Qdrant getQdrant() {
        return qdrant;
    }

    public Minio getMinio() {
        return minio;
    }

    public Ingest getIngest() {
        return ingest;
    }

    public Query getQuery() {
        return query;
    }

    /** 通义千问（DashScope）配置，同时用于 Embedding 与可选的 Chat */
    public static class Dashscope {

        private String apiKey;

        private String embeddingModel = "text-embedding-v2";

        private String chatModel = "qwen-plus";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }
    }

    /** 对话模型配置：provider 决定实际使用的实现 */
    public static class Llm {

        /** qwen 或 deepseek */
        private String provider = "qwen";

        private final Deepseek deepseek = new Deepseek();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Deepseek getDeepseek() {
            return deepseek;
        }
    }

    /** DeepSeek 走 OpenAI 兼容协议 */
    public static class Deepseek {

        private String apiKey;

        private String baseUrl = "https://api.deepseek.com";

        private String modelName = "deepseek-chat";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
    }

    /** Qdrant 向量库，使用 gRPC 端口（默认 6334） */
    public static class Qdrant {

        private String host = "127.0.0.1";

        private int port = 6334;

        private String collection = "rag_chunks";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }
    }

    /** MinIO 对象存储 */
    public static class Minio {

        private String endpoint = "http://127.0.0.1:9002";

        private String accessKey = "minioadmin";

        private String secretKey = "minioadmin";

        private String bucket = "rag-documents";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    /** 文档异步入库参数 */
    public static class Ingest {

        private String topic = "rag-document-ingest";

        private String consumerGroup = "rag-ingest-consumer";

        /** 单个分片的目标字符数 */
        private int chunkSize = 800;

        /** 相邻分片的重叠字符数 */
        private int chunkOverlap = 100;

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getConsumerGroup() {
            return consumerGroup;
        }

        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }
    }

    /** 检索问答参数 */
    public static class Query {

        private int defaultTopK = 5;

        private double minScore = 0.6;

        private int historyRounds = 3;

        /**
         * 系统提示词：约束模型的作答方式，要求尽量依据文档原文作答。
         * 可在此处或 application.yml 的 cloud.rag.query.system-prompt 中调整。
         */
        private String systemPrompt = """
                你是一个严谨的知识库问答助手，只能依据下面提供的资料回答用户问题。
                要求：
                1. 尽量使用资料中的原文作答，优先摘录、复述原文的关键句子，不要改写成同义表达，也不要润色、扩写或补充资料以外的信息。
                2. 资料中没有的内容不要编造，可以回答“资料中未提及该内容”。
                3. 回答使用中文，条理清晰，必要时分点说明，每一点尽量贴近原文措辞。
                4. 不要在回答中罗列资料编号。
                """;

        public int getDefaultTopK() {
            return defaultTopK;
        }

        public void setDefaultTopK(int defaultTopK) {
            this.defaultTopK = defaultTopK;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }

        public int getHistoryRounds() {
            return historyRounds;
        }

        public void setHistoryRounds(int historyRounds) {
            this.historyRounds = historyRounds;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }
    }
}

package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

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

    private final Ocr ocr = new Ocr();

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

    public Ocr getOcr() {
        return ocr;
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

        /** 单个子块的字符数：子块参与向量相似度计算 */
        private int chunkSize = 400;

        /** 相邻子块的重叠字符数 */
        private int chunkOverlap = 50;

        /** 单个父块的字符数：父块正文随子块入库，命中后作为 Prompt 输入 */
        private int parentChunkSize = 2000;

        /** 相邻父块的重叠字符数 */
        private int parentChunkOverlap = 200;

        private final HypotheticalQuestions hypotheticalQuestions = new HypotheticalQuestions();

        public HypotheticalQuestions getHypotheticalQuestions() {
            return hypotheticalQuestions;
        }

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

        public int getParentChunkSize() {
            return parentChunkSize;
        }

        public void setParentChunkSize(int parentChunkSize) {
            this.parentChunkSize = parentChunkSize;
        }

        public int getParentChunkOverlap() {
            return parentChunkOverlap;
        }

        public void setParentChunkOverlap(int parentChunkOverlap) {
            this.parentChunkOverlap = parentChunkOverlap;
        }
    }

    /**
     * 假设问题：入库时为每个分片预生成若干条它能够回答的问题，
     * 与正文一起向量化后作为额外检索入口，提升口语化提问的召回率。
     */
    public static class HypotheticalQuestions {

        private boolean enabled = true;

        /** 每个分片生成的问题数量 */
        private int count = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    /** 检索问答参数 */
    public static class Query {

        private int defaultTopK = 8;

        private double minScore = 0.6;

        private int historyRounds = 3;

        private final Rewrite rewrite = new Rewrite();

        public Rewrite getRewrite() {
            return rewrite;
        }

        /**
         * 系统提示词：只约束作答风格与格式，检索命中 / 未命中的差异由 RagQueryService 分支注入。
         * 可在此处或 application.yml 的 cloud.rag.query.system-prompt 中调整。
         */
        private String systemPrompt = """
                你是一个严谨的知识库问答助手。
                要求：
                1. 回答使用中文，条理清晰，必要时分点说明。
                2. 不要在回答中罗列资料编号。
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

    /**
     * 查询转换：检索前对用户问题做查询重写、回溯提示与子查询分解，
     * 再把多个检索式的结果合并去重，缓解原始提问与文档表述措辞不一致导致的漏召回。
     */
    public static class Rewrite {

        private boolean enabled = true;

        /** 子查询分解的数量上限 */
        private int maxSubQueries = 3;

        /** 单次检索允许使用的查询总数上限（含原始问题） */
        private int maxQueries = 4;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxSubQueries() {
            return maxSubQueries;
        }

        public void setMaxSubQueries(int maxSubQueries) {
            this.maxSubQueries = maxSubQueries;
        }

        public int getMaxQueries() {
            return maxQueries;
        }

        public void setMaxQueries(int maxQueries) {
            this.maxQueries = maxQueries;
        }
    }

    /**
     * 图片 OCR 配置：Python(PaddleOCR) 侧只暴露轻量 HTTP 接口，
     * Spring 侧通过 RocketMQ 异步投递识别请求，避免上传接口被识别耗时阻塞。
     */
    public static class Ocr {

        /** Python OCR 服务地址 */
        private String baseUrl = "http://127.0.0.1:8100";

        /** 允许上传的图片体积上限（MB），超过则在入库前直接拒绝 */
        private double maxImageMb = 10.0;

        /** 支持识别的图片扩展名（小写，不含点） */
        private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "bmp", "webp");

        /** OCR 识别请求队列 */
        private String topic = "rag-ocr-request";

        private String consumerGroup = "rag-ocr-consumer";

        /** 建立连接的超时时间（毫秒） */
        private int connectTimeout = 3000;

        /** 单次识别的最长等待时间（毫秒），首次识别需加载模型，留足余量 */
        private int readTimeout = 60000;

        /** 图片体积上限对应的字节数 */
        public long getMaxImageBytes() {
            return (long) (maxImageMb * 1024 * 1024);
        }

        /** 按文件名判断是否为受支持的可识别图片 */
        public boolean isImage(String fileName) {
            if (fileName == null) {
                return false;
            }
            int dot = fileName.lastIndexOf('.');
            if (dot < 0 || dot == fileName.length() - 1) {
                return false;
            }
            return allowedExtensions.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public double getMaxImageMb() {
            return maxImageMb;
        }

        public void setMaxImageMb(double maxImageMb) {
            this.maxImageMb = maxImageMb;
        }

        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }

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

        public int getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public int getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }
    }
}

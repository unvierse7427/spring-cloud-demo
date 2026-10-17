package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.dto.AskRequest;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.QaHistoryEntry;
import com.example.rag.dto.SourceRef;
import com.example.rag.entity.KbDocument;
import com.example.rag.support.QaHistoryStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 问答：把问题向量化 -> 在 Qdrant 中按知识库检索 -> 拼装提示词交给大模型 -> 返回答案与引用来源
 */
@Service
public class RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryService.class);

    /** 单条引用片段在响应中的最大长度 */
    private static final int SNIPPET_MAX_LENGTH = 300;

    /** 检索时的放大倍数：先多取候选，再按用户权限过滤后截取 topK */
    private static final int RETRIEVE_MULTIPLIER = 4;

    /** 单次检索的候选上限，避免权限过滤时取到过多数据 */
    private static final int MAX_RETRIEVE = 100;

    /** 提示词骨架：由“可配置的系统提示词 + 检索资料 + 用户问题”拼装而成 */
    private static final String PROMPT_TEMPLATE = """
            %s

            资料：
            %s

            用户问题：%s
            """;

    private final KnowledgeBaseService knowledgeBaseService;

    private final EmbeddingModel embeddingModel;

    private final EmbeddingStore<TextSegment> embeddingStore;

    private final ChatModel chatModel;

    private final RagProperties properties;

    private final QaHistoryStore historyStore;

    public RagQueryService(KnowledgeBaseService knowledgeBaseService, EmbeddingModel embeddingModel,
                           EmbeddingStore<TextSegment> embeddingStore, ChatModel chatModel,
                           RagProperties properties, QaHistoryStore historyStore) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatModel = chatModel;
        this.properties = properties;
        this.historyStore = historyStore;
    }

    public AskResponse ask(Long kbId, AskRequest request) {
        knowledgeBaseService.getRequired(kbId);
        if (request == null || !StringUtils.hasText(request.getQuestion())) {
            throw new IllegalArgumentException("提问内容不能为空");
        }
        String question = request.getQuestion().trim();

        int topK = request.getTopK() != null && request.getTopK() > 0
                ? request.getTopK() : properties.getQuery().getDefaultTopK();
        int retrieveSize = Math.min(topK * RETRIEVE_MULTIPLIER, MAX_RETRIEVE);

        Embedding queryEmbedding = embeddingModel.embed(question).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(retrieveSize)
                .minScore(properties.getQuery().getMinScore())
                // 只在当前知识库、且只取文档最新版本的片段作答（历史版本仅作存档）
                .filter(MetadataFilterBuilder.metadataKey("kbId").isEqualTo(String.valueOf(kbId))
                        .and(MetadataFilterBuilder.metadataKey("latest")
                                .isEqualTo(String.valueOf(KbDocument.LATEST_YES))))
                .build();
        List<EmbeddingMatch<TextSegment>> candidates = embeddingStore.search(searchRequest).matches();
        // 只保留当前用户有权限查看的文档片段，保证问答不会引用越权内容
        List<EmbeddingMatch<TextSegment>> matches = filterVisible(candidates, topK);

        AskResponse response = new AskResponse();
        List<SourceRef> sources = new ArrayList<>(matches.size());
        for (EmbeddingMatch<TextSegment> match : matches) {
            sources.add(toSourceRef(match));
        }
        response.setSources(sources);

        if (matches.isEmpty()) {
            response.setAnswer("知识库中未检索到与该问题相关的内容，请先上传相关文档或换一种问法。");
            historyStore.record(kbId, question, response);
            return response;
        }

        String prompt = PROMPT_TEMPLATE.formatted(
                properties.getQuery().getSystemPrompt(), buildContext(matches), question);
        String answer = chatModel.chat(prompt);
        response.setAnswer(answer);
        log.info("知识库问答完成：kbId={}, 命中片段={}", kbId, matches.size());
        historyStore.record(kbId, question, response);
        return response;
    }

    /** 当前用户在该知识库下的问答历史，最新在前 */
    public List<QaHistoryEntry> history(Long kbId) {
        knowledgeBaseService.getRequired(kbId);
        return historyStore.list(kbId);
    }

    /** 清空当前用户在该知识库下的问答历史 */
    public void clearHistory(Long kbId) {
        knowledgeBaseService.getRequired(kbId);
        historyStore.clear(kbId);
    }

    /**
     * 按用户权限过滤检索候选：先取出候选片段所属的文档 id，
     * 交给知识库服务判定可见性，只保留可见文档的片段，最后截取前 topK 条。
     */
    private List<EmbeddingMatch<TextSegment>> filterVisible(List<EmbeddingMatch<TextSegment>> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> documentIds = candidates.stream()
                .map(match -> parseLong(match.embedded().metadata().getString("docId")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> visibleIds = knowledgeBaseService.visibleDocumentIds(documentIds);
        return candidates.stream()
                .filter(match -> visibleIds.contains(parseLong(match.embedded().metadata().getString("docId"))))
                .limit(topK)
                .collect(Collectors.toList());
    }

    private String buildContext(List<EmbeddingMatch<TextSegment>> matches) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < matches.size(); i++) {
            TextSegment segment = matches.get(i).embedded();
            builder.append('[').append(i + 1).append("] ");
            String fileName = segment.metadata().getString("fileName");
            if (StringUtils.hasText(fileName)) {
                builder.append('（').append(fileName).append('）');
            }
            builder.append('\n').append(segment.text()).append("\n\n");
        }
        return builder.toString().trim();
    }

    private SourceRef toSourceRef(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        SourceRef ref = new SourceRef();
        ref.setDocumentId(parseLong(segment.metadata().getString("docId")));
        ref.setFileName(segment.metadata().getString("fileName"));
        ref.setVersion(parseInt(segment.metadata().getString("version")));
        ref.setScore(match.score());
        ref.setSnippet(abbreviate(segment.text()));
        return ref;
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > SNIPPET_MAX_LENGTH ? text.substring(0, SNIPPET_MAX_LENGTH) + "..." : text;
    }
}

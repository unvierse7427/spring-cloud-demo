package com.example.rag.service;

import com.example.rag.config.RagProperties;
import com.example.rag.dto.AskRequest;
import com.example.rag.dto.AskResponse;
import com.example.rag.dto.QaChatMessage;
import com.example.rag.dto.QaSessionView;
import com.example.rag.dto.SourceRef;
import com.example.rag.entity.KbDocument;
import com.example.rag.support.QaHistoryStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * RAG 问答：把问题向量化 -> 在 Qdrant 中按知识库检索 -> 拼装提示词交给大模型 -> 返回答案与引用来源
 *
 * <p>检索前先由 {@link QueryTransformService} 把用户问题扩展成多个检索式（重写 / 回溯 / 子查询），
 * 逐个检索后合并去重；入库时为子块预生成的“假设问题”同样是命中来源之一。
 * <p>向量库里存的是子块，召回后统一替换为它的父块再喂给模型：子块保证相似度计算准确，
 * 父块保证上下文完整。
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

    /** 子块上附带的父块正文，命中子块后用父块（而不是子块）作为 Prompt 输入 */
    private static final String METADATA_PARENT_TEXT = "parentText";

    /** 向量条目类型：入库时由子块生成的假设问题 */
    private static final String VECTOR_TYPE_QUESTION = "question";

    /** Qdrant payload 中存放片段正文的键 */
    private static final String PAYLOAD_TEXT_KEY = "text_segment";

    /** 一次返回的推荐问题条数 */
    private static final int DEFAULT_SUGGESTION_LIMIT = 6;

    /** 推荐问题的条数上限 */
    private static final int MAX_SUGGESTION_LIMIT = 12;

    /** 扫描假设问题时的条数上限，扫完再按权限过滤并随机抽样 */
    private static final int SUGGESTION_SCAN_LIMIT = 200;

    /** 扫描向量库的超时时间（秒） */
    private static final long SUGGESTION_TIMEOUT_SECONDS = 10;

    /** 回答下方展示的追问建议条数 */
    private static final int MATCHED_SUGGESTION_LIMIT = 3;

    /** 认为两个推荐问题“太像了”的阈值，超过后不再同时展示 */
    private static final double SUGGESTION_SIMILARITY_THRESHOLD = 0.72D;

    /** SSE 会话超时时间（毫秒） */
    private static final long STREAM_TIMEOUT_MS = 120000L;

    /**
     * 检索命中的提示词：严格依据检索到的片段作答，禁止改写、润色、扩写。
     * 格式要求（中文、可分点、不罗列资料编号）由可配置的系统提示词统一约束。
     */
    private static final String MATCHED_PROMPT_TEMPLATE = """
            %s

            本次已从知识库检索到相关资料，请严格依据下面的正文片段回答用户问题。
            规则：
            1. 尽量使用片段中的原文作答，优先摘录、复述原文的关键句子，不要改写成同义表达，也不要润色、扩写。
            2. 不要把片段里没有的内容当作资料事实来陈述，也不要编造片段中没有的信息。
            3. 如果片段只覆盖了问题的一部分，就回答能确认的部分，并说明其余部分在检索到的片段中没有提及。

            最近对话：
            %s

            检索到的正文片段：
            %s

            用户问题：%s
            """;

    /**
     * 检索未命中的提示词：不注入任何片段，按通用问答处理。
     * 此时不再要求模型“资料没提及就拒答”，而是正常回答用户问题。
     */
    private static final String UNMATCHED_PROMPT_TEMPLATE = """
            %s

            本次没有从知识库中检索到与用户问题相关的正文片段，
            请直接按你已有的知识正常回答用户问题，不要因为缺少资料而拒答、敷衍或只回复“未提及”。
            另外：不要声称回答来自知识库，也不需要说明知识库中没有相关资料。

            最近对话：
            %s

            用户问题：%s
            """;

    private final KnowledgeBaseService knowledgeBaseService;

    private final EmbeddingModel embeddingModel;

    private final EmbeddingStore<TextSegment> embeddingStore;

    private final ChatModel chatModel;

    private final RagProperties properties;

    private final QaHistoryStore historyStore;

    private final QueryTransformService queryTransformService;

    private final QdrantClient qdrantClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RagQueryService(KnowledgeBaseService knowledgeBaseService, EmbeddingModel embeddingModel,
                           EmbeddingStore<TextSegment> embeddingStore, ChatModel chatModel,
                           RagProperties properties, QaHistoryStore historyStore,
                           QueryTransformService queryTransformService, QdrantClient qdrantClient) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatModel = chatModel;
        this.properties = properties;
        this.historyStore = historyStore;
        this.queryTransformService = queryTransformService;
        this.qdrantClient = qdrantClient;
    }

    public AskResponse ask(Long kbId, AskRequest request) {
        String question = validateQuestion(kbId, request);
        AskResponse response = generateAnswer(kbId, request.getSessionId(), question, request.getTopK());
        String sessionId = historyStore.recordTurn(kbId, request.getSessionId(), question, response);
        response.setSessionId(sessionId);
        return response;
    }

    /** 知识库问答（SSE）：在同一会话里持续提问，回答逐段推送到前端 */
    public SseEmitter askStream(Long kbId, AskRequest request) {
        String question = validateQuestion(kbId, request);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        try {
            AskResponse response = generateAnswer(kbId, request.getSessionId(), question, request.getTopK());
            String sessionId = historyStore.recordTurn(kbId, request.getSessionId(), question, response);
            response.setSessionId(sessionId);
            emitter.send(SseEmitter.event().name("session").data(sessionId, MediaType.TEXT_PLAIN));
            for (int i = 0; i < response.getAnswer().length(); i++) {
                emitter.send(SseEmitter.event().name("delta")
                        .data(String.valueOf(response.getAnswer().charAt(i)), MediaType.TEXT_PLAIN));
            }
            emitter.send(SseEmitter.event().name("done")
                    .data(objectMapper.writeValueAsString(response), MediaType.TEXT_PLAIN));
            emitter.complete();
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(defaultText(e.getMessage(), "问答失败"), MediaType.TEXT_PLAIN));
            } catch (Exception ignored) {
                log.debug("发送 SSE 错误事件失败：{}", ignored.getMessage());
            }
            emitter.complete();
        }
        return emitter;
    }

    private String validateQuestion(Long kbId, AskRequest request) {
        knowledgeBaseService.getRequired(kbId);
        if (request == null || !StringUtils.hasText(request.getQuestion())) {
            throw new IllegalArgumentException("提问内容不能为空");
        }
        return request.getQuestion().trim();
    }

    private AskResponse generateAnswer(Long kbId, String sessionId, String question, Integer topKValue) {
        AskResponse response = new AskResponse();
        response.setSources(List.of());

        int topK = topKValue != null && topKValue > 0
                ? topKValue : properties.getQuery().getDefaultTopK();
        int retrieveSize = Math.min(topK * RETRIEVE_MULTIPLIER, MAX_RETRIEVE);

        String historyContext = buildHistoryContext(kbId, sessionId);
        List<String> queries = resolveQueries(question, historyContext);
        List<EmbeddingMatch<TextSegment>> matches = filterVisible(retrieve(kbId, queries, retrieveSize), topK);

        List<SourceRef> sources = new ArrayList<>(matches.size());
        for (EmbeddingMatch<TextSegment> match : matches) {
            sources.add(toSourceRef(match));
        }
        response.setSources(sources);
        response.setSuggestions(matchSuggestions(kbId, matches, question));

        if (matches.isEmpty()) {
            String unmatchedPrompt = UNMATCHED_PROMPT_TEMPLATE.formatted(
                    properties.getQuery().getSystemPrompt(),
                    historyContext,
                    question);
            response.setAnswer(chatModel.chat(unmatchedPrompt));
            log.info("知识库问答完成：kbId={}, 检索式={}, 未命中检索，改用通用问答", kbId, queries.size());
            return response;
        }

        String prompt = MATCHED_PROMPT_TEMPLATE.formatted(
                properties.getQuery().getSystemPrompt(),
                historyContext,
                buildContext(matches),
                question);
        response.setAnswer(chatModel.chat(prompt));
        log.info("知识库问答完成：kbId={}, 检索式={}, 命中片段={}", kbId, queries.size(), matches.size());
        return response;
    }

    /** 本次检索要用的查询列表；未启用查询转换时只使用用户的原始问题 */
    private List<String> resolveQueries(String question, String historyContext) {
        RagProperties.Rewrite rewrite = properties.getQuery().getRewrite();
        if (!rewrite.isEnabled()) {
            return List.of(question);
        }
        return queryTransformService.transform(question, historyContext,
                rewrite.getMaxSubQueries(), rewrite.getMaxQueries());
    }

    /**
     * 用多个检索式分别召回，再按父块归并：同一父块下的多个子块、以及子块自己的假设问题，
     * 都可能同时命中，这里统一保留相似度最高的那条，保证一个父块只喂给模型一次。
     */
    private List<EmbeddingMatch<TextSegment>> retrieve(Long kbId, List<String> queries, int retrieveSize) {
        Map<String, EmbeddingMatch<TextSegment>> best = new LinkedHashMap<>();
        for (String query : queries) {
            for (EmbeddingMatch<TextSegment> match : search(kbId, query, retrieveSize)) {
                best.merge(dedupeKey(match), match,
                        (left, right) -> left.score() >= right.score() ? left : right);
            }
        }
        return best.values().stream()
                .sorted(Comparator.comparingDouble(EmbeddingMatch<TextSegment>::score).reversed())
                .collect(Collectors.toList());
    }

    private List<EmbeddingMatch<TextSegment>> search(Long kbId, String query, int retrieveSize) {
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(query).content())
                .maxResults(retrieveSize)
                .minScore(properties.getQuery().getMinScore())
                // 只在当前知识库、且只取文档最新版本的片段作答（历史版本仅作存档）
                .filter(MetadataFilterBuilder.metadataKey("kbId").isEqualTo(String.valueOf(kbId))
                        .and(MetadataFilterBuilder.metadataKey("latest")
                                .isEqualTo(String.valueOf(KbDocument.LATEST_YES))))
                .build();
        return embeddingStore.search(searchRequest).matches();
    }

    /** 有父块编号时按父块归并，同一父块下的子块与假设问题命中只保留最高分；老数据则退化为按分片/正文归并 */
    private String dedupeKey(EmbeddingMatch<TextSegment> match) {
        Metadata metadata = match.embedded().metadata();
        String docId = metadata.getString("docId");
        String parentIndex = metadata.getString("parentIndex");
        if (parentIndex != null) {
            return docId + "|p|" + parentIndex;
        }
        String chunkIndex = metadata.getString("chunkIndex");
        return chunkIndex == null
                ? docId + "|t|" + match.embedded().text()
                : docId + "|c|" + chunkIndex;
    }

    /** 命中子块（或它的假设问题）时用父块正文，老数据回退到子块自身正文 */
    private String segmentText(TextSegment segment) {
        String parentText = segment.metadata().getString(METADATA_PARENT_TEXT);
        return StringUtils.hasText(parentText) ? parentText : segment.text();
    }

    /** 当前用户在该知识库下的会话列表，最近更新的会话在前 */
    public List<QaSessionView> history(Long kbId) {
        knowledgeBaseService.getRequired(kbId);
        return historyStore.listSessions(kbId);
    }

    /** 当前用户查看指定会话下的全部消息 */
    public List<QaChatMessage> historyMessages(Long kbId, String sessionId) {
        knowledgeBaseService.getRequired(kbId);
        return historyStore.listMessages(kbId, sessionId);
    }

    /** 清空当前用户在该知识库下的全部会话 */
    public void clearHistory(Long kbId) {
        knowledgeBaseService.getRequired(kbId);
        historyStore.clear(kbId);
    }

    /**
     * 从知识库各切片预生成的“假设问题”里随机取几条，作为界面上的推荐提问。
     *
     * <p>只返回当前用户可见文档的问题；问题库为空（如文档尚未重新入库）时返回空列表。
     */
    public List<String> suggestions(Long kbId, Integer limit) {
        knowledgeBaseService.getRequired(kbId);
        int size = limit != null && limit > 0
                ? Math.min(limit, MAX_SUGGESTION_LIMIT) : DEFAULT_SUGGESTION_LIMIT;

        List<QuestionPoint> candidates = scrollQuestions(Common.Filter.newBuilder()
                .addMust(ConditionFactory.matchKeyword("kbId", String.valueOf(kbId)))
                .addMust(ConditionFactory.matchKeyword("latest", String.valueOf(KbDocument.LATEST_YES)))
                .addMust(ConditionFactory.matchKeyword("vectorType", VECTOR_TYPE_QUESTION))
                .build());
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> documentIds = candidates.stream()
                .map(QuestionPoint::documentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> visibleIds = knowledgeBaseService.visibleDocumentIds(documentIds);

        List<String> questions = candidates.stream()
                .filter(item -> visibleIds.contains(item.documentId()))
                .map(QuestionPoint::text)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(questions);
        return questions.size() > size ? new ArrayList<>(questions.subList(0, size)) : questions;
    }

    /**
     * 回答下方的 1+1+1 发散推荐：
     * 1. 当前命中片段 1 条
     * 2. 同文档其他片段 1 条
     * 3. 其他命中文档 / 其他文档扩展 1 条
     *
     * <p>这样既保留顺着当前回答继续追问的入口，也尽量把用户从单一片段里带出来。
     */
    private List<String> matchSuggestions(Long kbId, List<EmbeddingMatch<TextSegment>> matches, String question) {
        if (matches.isEmpty()) {
            return List.of();
        }
        List<QuestionPoint> candidates = visibleQuestionCandidates(kbId);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Metadata primaryMetadata = matches.get(0).embedded().metadata();
        Long primaryDocId = parseLong(primaryMetadata.getString("docId"));
        String primaryOriginKey = originKey(primaryMetadata);
        if (primaryDocId == null || !StringUtils.hasText(primaryOriginKey)) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>(MATCHED_SUGGESTION_LIMIT);

        // 1. 当前命中片段：保留一个最贴近本轮回答的问题
        addSuggestion(suggestions, questionsFromSameOrigin(candidates, primaryOriginKey), question);

        // 2. 同文档其他片段：从同一文档的其他 origin 里取一个，帮助用户横向补充上下文
        addSuggestion(suggestions, questionsFromSameDocument(candidates, primaryDocId, primaryOriginKey), question);

        // 3. 其他命中文档：优先从本次其它高分命中文档里取一个；没有时再回退到知识库其它文档
        addSuggestion(suggestions, questionsFromOtherMatchedDocuments(candidates, matches, primaryDocId), question);
        if (suggestions.size() < MATCHED_SUGGESTION_LIMIT) {
            addSuggestion(suggestions, questionsFromOtherDocuments(candidates, primaryDocId), question);
        }

        // 最后兜底，避免因为去重过严导致不足 3 条
        if (suggestions.size() < MATCHED_SUGGESTION_LIMIT) {
            fillSuggestions(suggestions, candidates.stream()
                    .map(QuestionPoint::text)
                    .collect(Collectors.toList()), question);
        }
        return suggestions;
    }

    /** 片段定位键：与 {@link #originCondition} 用同一组元数据，把扫到的问题对回命中片段 */
    private String originKey(Metadata metadata) {
        return originKey(metadata.getString("docId"), metadata.getString("parentIndex"),
                metadata.getString("childIndex"), metadata.getString("chunkIndex"));
    }

    private String originKey(String docId, String parentIndex, String childIndex, String chunkIndex) {
        if (!StringUtils.hasText(docId)) {
            return null;
        }
        if (StringUtils.hasText(parentIndex)) {
            return docId + "|p|" + parentIndex + "|" + (childIndex == null ? "" : childIndex);
        }
        return StringUtils.hasText(chunkIndex) ? docId + "|c|" + chunkIndex : null;
    }

    /** 按条件扫描问题条目 */
    private List<QuestionPoint> scrollQuestions(Common.Filter filter) {
        Points.ScrollPoints request = Points.ScrollPoints.newBuilder()
                .setCollectionName(properties.getQdrant().getCollection())
                .setFilter(filter)
                .setLimit(SUGGESTION_SCAN_LIMIT)
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                .build();
        try {
            Points.ScrollResponse response = qdrantClient.scrollAsync(request)
                    .get(SUGGESTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return response.getResultList().stream()
                    .map(point -> new QuestionPoint(
                            payloadText(point, PAYLOAD_TEXT_KEY),
                            originKey(payloadText(point, "docId"), payloadText(point, "parentIndex"),
                                    payloadText(point, "childIndex"), payloadText(point, "chunkIndex")),
                            parseLong(payloadText(point, "docId"))))
                    .filter(item -> StringUtils.hasText(item.text()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("读取切片预置问题失败：{}", e.getMessage());
            return List.of();
        }
    }

    /** 取 payload 中的字符串字段，缺失时返回 null */
    private String payloadText(Points.RetrievedPoint point, String key) {
        JsonWithInt.Value value = point.getPayloadMap().get(key);
        return value == null || !StringUtils.hasText(value.getStringValue()) ? null : value.getStringValue();
    }

    /** 当前知识库下、且当前用户有权限查看的问题候选池 */
    private List<QuestionPoint> visibleQuestionCandidates(Long kbId) {
        List<QuestionPoint> candidates = scrollQuestions(Common.Filter.newBuilder()
                .addMust(ConditionFactory.matchKeyword("kbId", String.valueOf(kbId)))
                .addMust(ConditionFactory.matchKeyword("latest", String.valueOf(KbDocument.LATEST_YES)))
                .addMust(ConditionFactory.matchKeyword("vectorType", VECTOR_TYPE_QUESTION))
                .build());
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Long> documentIds = candidates.stream()
                .map(QuestionPoint::documentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> visibleIds = knowledgeBaseService.visibleDocumentIds(documentIds);
        return candidates.stream()
                .filter(item -> visibleIds.contains(item.documentId()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<String> questionsFromSameOrigin(List<QuestionPoint> candidates, String originKey) {
        return shuffledTexts(candidates.stream()
                .filter(item -> originKey.equals(item.originKey()))
                .map(QuestionPoint::text)
                .collect(Collectors.toList()));
    }

    private List<String> questionsFromSameDocument(List<QuestionPoint> candidates, Long docId, String currentOriginKey) {
        return shuffledTexts(candidates.stream()
                .filter(item -> Objects.equals(docId, item.documentId()))
                .filter(item -> !currentOriginKey.equals(item.originKey()))
                .map(QuestionPoint::text)
                .collect(Collectors.toList()));
    }

    private List<String> questionsFromOtherMatchedDocuments(List<QuestionPoint> candidates,
                                                            List<EmbeddingMatch<TextSegment>> matches,
                                                            Long primaryDocId) {
        List<String> texts = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            Long docId = parseLong(match.embedded().metadata().getString("docId"));
            if (docId == null || Objects.equals(docId, primaryDocId) || !visited.add(docId)) {
                continue;
            }
            texts.addAll(shuffledTexts(candidates.stream()
                    .filter(item -> Objects.equals(docId, item.documentId()))
                    .map(QuestionPoint::text)
                    .collect(Collectors.toList())));
        }
        return texts;
    }

    private List<String> questionsFromOtherDocuments(List<QuestionPoint> candidates, Long primaryDocId) {
        List<String> texts = candidates.stream()
                .filter(item -> !Objects.equals(primaryDocId, item.documentId()))
                .map(QuestionPoint::text)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(texts);
        return texts;
    }

    private List<String> shuffledTexts(List<String> texts) {
        List<String> distinct = texts.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(distinct);
        return distinct;
    }

    private void addSuggestion(List<String> suggestions, List<String> candidates, String currentQuestion) {
        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)
                    || isSameAsCurrentQuestion(candidate, currentQuestion)
                    || isTooSimilar(suggestions, candidate)) {
                continue;
            }
            suggestions.add(candidate);
            if (suggestions.size() >= MATCHED_SUGGESTION_LIMIT) {
                return;
            }
            return;
        }
    }

    /** 兜底补位：从候选池里持续补充，直到凑满 3 条或候选耗尽 */
    private void fillSuggestions(List<String> suggestions, List<String> candidates, String currentQuestion) {
        for (String candidate : candidates) {
            if (suggestions.size() >= MATCHED_SUGGESTION_LIMIT) {
                return;
            }
            if (!StringUtils.hasText(candidate)
                    || isSameAsCurrentQuestion(candidate, currentQuestion)
                    || isTooSimilar(suggestions, candidate)) {
                continue;
            }
            suggestions.add(candidate);
        }
    }

    private boolean isSameAsCurrentQuestion(String candidate, String currentQuestion) {
        return StringUtils.hasText(currentQuestion)
                && similarity(candidate, currentQuestion) >= SUGGESTION_SIMILARITY_THRESHOLD;
    }

    private boolean isTooSimilar(List<String> suggestions, String candidate) {
        for (String existing : suggestions) {
            if (existing.equals(candidate) || similarity(existing, candidate) >= SUGGESTION_SIMILARITY_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private double similarity(String left, String right) {
        String normalizedLeft = normalizeQuestion(left);
        String normalizedRight = normalizeQuestion(right);
        if (!StringUtils.hasText(normalizedLeft) || !StringUtils.hasText(normalizedRight)) {
            return 0D;
        }
        if (normalizedLeft.equals(normalizedRight)) {
            return 1D;
        }
        Set<String> leftTerms = questionTerms(normalizedLeft);
        Set<String> rightTerms = questionTerms(normalizedRight);
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) {
            return 0D;
        }
        Set<String> union = new HashSet<>(leftTerms);
        union.addAll(rightTerms);
        Set<String> intersection = new HashSet<>(leftTerms);
        intersection.retainAll(rightTerms);
        return union.isEmpty() ? 0D : (double) intersection.size() / union.size();
    }

    private String normalizeQuestion(String question) {
        return question == null ? "" : question.toLowerCase().replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "");
    }

    private Set<String> questionTerms(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        if (text.length() == 1) {
            return Set.of(text);
        }
        Set<String> terms = new HashSet<>();
        for (int i = 0; i < text.length() - 1; i++) {
            terms.add(text.substring(i, i + 2));
        }
        return terms;
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
            builder.append('\n').append(segmentText(segment)).append("\n\n");
        }
        return builder.toString().trim();
    }

    private String buildHistoryContext(Long kbId, String sessionId) {
        int rounds = Math.max(properties.getQuery().getHistoryRounds(), 0);
        if (rounds == 0) {
            return "（未启用多轮上下文）";
        }
        return historyStore.buildHistoryContext(kbId, sessionId, rounds);
    }

    private String defaultText(String text, String fallback) {
        return StringUtils.hasText(text) ? text : fallback;
    }

    private SourceRef toSourceRef(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        SourceRef ref = new SourceRef();
        ref.setDocumentId(parseLong(segment.metadata().getString("docId")));
        ref.setFileName(segment.metadata().getString("fileName"));
        ref.setVersion(parseInt(segment.metadata().getString("version")));
        ref.setScore(match.score());
        ref.setSnippet(abbreviate(segmentText(segment)));
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

    /** 扫描出的问题条目：问题文本 + 所属片段键 + 所属文档（用于权限过滤） */
    private record QuestionPoint(String text, String originKey, Long documentId) {
    }
}

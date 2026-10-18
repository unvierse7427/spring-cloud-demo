package com.example.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 查询转换：检索前把用户问题扩展成多个检索式。
 *
 * <p>用户的原始查询往往不是最优的检索形式，一次问答里同时做三件事：
 * <ul>
 *     <li>查询重写（Rewrite）：补全指代、去掉口语与冗余，改写为独立完整的检索句；</li>
 *     <li>回溯提示（Step-back）：生成更上位、更概括的问题，用于召回背景性知识；</li>
 *     <li>子查询分解（Sub-query）：把复合问题拆成若干可独立检索的子问题。</li>
 * </ul>
 * 三者由一次大模型调用合并产出。调用或解析失败时退化为只使用原始问题，不影响问答链路。
 */
@Service
public class QueryTransformService {

    private static final Logger log = LoggerFactory.getLogger(QueryTransformService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String PROMPT_TEMPLATE = """
            你是检索优化助手。请针对用户问题做三件事，并且只用 JSON 返回结果，不要输出 JSON 以外的任何内容：
            {
              "rewritten": "把用户问题改写为独立、完整、有利于向量检索的一句话，补全指代，去掉口语和冗余",
              "stepBack": "一个比用户问题更上位、更概括的问题，用于召回背景性知识",
              "subQueries": ["把用户问题拆成的最多 %d 个可以独立检索的子问题"]
            }
            如果用户问题本身已经很清晰，rewritten 可以与原问题相同，subQueries 可以为空数组。

            最近对话：
            %s

            用户问题：%s
            """;

    private final ChatModel chatModel;

    public QueryTransformService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 返回本次检索要用的查询列表，第一个元素固定是用户的原始问题。
     * 后续元素是重写、回溯与子查询的结果，按数量上限截断。
     */
    public List<String> transform(String question, String historyContext, int maxSubQueries, int maxQueries) {
        Set<String> queries = new LinkedHashSet<>();
        queries.add(question);
        try {
            JsonNode node = parseJson(chatModel.chat(
                    PROMPT_TEMPLATE.formatted(maxSubQueries, historyContext, question)));
            if (node != null) {
                addIfPresent(queries, node.path("rewritten").asText(null));
                addIfPresent(queries, node.path("stepBack").asText(null));
                for (JsonNode sub : node.path("subQueries")) {
                    if (queries.size() >= maxQueries) {
                        break;
                    }
                    addIfPresent(queries, sub.asText(null));
                }
            }
        } catch (Exception e) {
            log.warn("查询转换失败，仅用原始问题检索：{}", e.getMessage());
        }
        List<String> result = new ArrayList<>(queries);
        return result.size() > maxQueries ? new ArrayList<>(result.subList(0, maxQueries)) : result;
    }

    /** 模型可能把 JSON 包在代码块里或前后加说明，这里截取最外层的一对花括号 */
    private JsonNode parseJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("查询转换结果中未找到 JSON，仅用原始问题检索");
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(raw.substring(start, end + 1));
        } catch (Exception e) {
            log.warn("查询转换结果不是合法 JSON，仅用原始问题检索：{}", e.getMessage());
            return null;
        }
    }

    private void addIfPresent(Set<String> queries, String value) {
        if (StringUtils.hasText(value)) {
            queries.add(value.trim());
        }
    }
}

package com.example.rag.service;

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
 * 为分片预生成“假设问题”：即该分片能够回答的用户提问。
 *
 * <p>这些问题会和分片正文一起写入向量库，作为额外的检索入口。用户的口语化提问
 * 往往与文档的书面表述措辞差异较大，直接比对正文容易漏召回；而比对“问题”则更贴近
 * 用户的表达习惯，能提高匹配准确度。命中问题时，回答引用的仍是它所属的正文分片。
 */
@Service
public class HypotheticalQuestionService {

    private static final Logger log = LoggerFactory.getLogger(HypotheticalQuestionService.class);

    /** 参与生成的正文长度上限，超长片段截断，避免拖慢生成 */
    private static final int SOURCE_MAX_LENGTH = 2000;

    private static final String PROMPT_TEMPLATE = """
            请阅读下面的文本片段，站在用户的角度，生成 %d 个该片段能够准确回答的问题。
            要求：
            1. 问题要具体、口语化，像真实用户会问的话。
            2. 每个问题都必须能由下面的片段回答，不要涉及片段之外的内容。
            3. 每行一个问题，不要编号，不要输出任何解释。

            文本片段：
            %s
            """;

    private final ChatModel chatModel;

    public HypotheticalQuestionService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 生成假设问题；调用失败时返回空列表，不影响正文入库。
     */
    public List<String> generate(String chunkText, int count) {
        if (!StringUtils.hasText(chunkText) || count <= 0) {
            return List.of();
        }
        String source = chunkText.length() > SOURCE_MAX_LENGTH
                ? chunkText.substring(0, SOURCE_MAX_LENGTH) : chunkText;
        try {
            return parse(chatModel.chat(PROMPT_TEMPLATE.formatted(count, source)), count);
        } catch (Exception e) {
            log.warn("生成假设问题失败，该分片仅按正文入库：{}", e.getMessage());
            return List.of();
        }
    }

    /** 模型偶尔会带上 “1. ” 之类的编号前缀，这里逐行清洗并去重 */
    private List<String> parse(String raw, int count) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        Set<String> questions = new LinkedHashSet<>();
        for (String line : raw.split("\\r?\\n")) {
            String question = line.trim().replaceFirst("^(\\d+[.、)]|[-*•])\\s*", "");
            if (StringUtils.hasText(question)) {
                questions.add(question);
            }
            if (questions.size() >= count) {
                break;
            }
        }
        return new ArrayList<>(questions);
    }
}

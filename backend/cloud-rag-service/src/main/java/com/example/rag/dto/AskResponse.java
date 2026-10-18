package com.example.rag.dto;

import java.util.List;

/**
 * 知识库问答响应
 */
public class AskResponse {

    /** 回答所属的会话 id，前端用它继续多轮追问 */
    private String sessionId;

    /** 模型给出的回答 */
    private String answer;

    /** 回答引用的资料片段 */
    private List<SourceRef> sources;

    /** 本次命中片段预生成的假设问题，展示在回答下方供继续追问 */
    private List<String> suggestions;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<SourceRef> getSources() {
        return sources;
    }

    public void setSources(List<SourceRef> sources) {
        this.sources = sources;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }
}

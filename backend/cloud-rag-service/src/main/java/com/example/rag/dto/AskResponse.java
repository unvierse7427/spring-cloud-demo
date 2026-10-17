package com.example.rag.dto;

import java.util.List;

/**
 * 知识库问答响应
 */
public class AskResponse {

    /** 模型给出的回答 */
    private String answer;

    /** 回答引用的资料片段 */
    private List<SourceRef> sources;

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
}

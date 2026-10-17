package com.example.rag.dto;

/**
 * 知识库提问请求
 */
public class AskRequest {

    private String question;

    /** 检索的分片数量，缺省取配置值 */
    private Integer topK;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}

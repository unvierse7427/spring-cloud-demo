package com.example.rag.dto;

import java.util.List;

/**
 * 一条智能问答历史，按用户维度记录在 Redis 中
 */
public class QaHistoryEntry {

    /** 历史记录唯一标识，前端列表 key 使用 */
    private String id;

    private Long kbId;

    private Long userId;

    private String username;

    /** 用户提出的问题 */
    private String question;

    /** 模型给出的回答 */
    private String answer;

    /** 回答引用的资料片段 */
    private List<SourceRef> sources;

    private String createTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
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

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}

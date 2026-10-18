package com.example.rag.dto;

import java.util.List;

/**
 * 会话内的一条聊天消息
 */
public class QaChatMessage {

    public static final String ROLE_USER = "USER";

    public static final String ROLE_ASSISTANT = "ASSISTANT";

    private String id;

    private String sessionId;

    private Long kbId;

    private String role;

    private String content;

    /** 助手消息引用的资料片段；用户消息为空 */
    private List<SourceRef> sources;

    /** 助手消息结尾给出的继续追问建议；用户消息为空 */
    private List<String> suggestions;

    private String createTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}

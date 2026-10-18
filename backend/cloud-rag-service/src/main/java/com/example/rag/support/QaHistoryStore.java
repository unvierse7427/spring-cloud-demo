package com.example.rag.support;

import com.example.rag.dto.AskResponse;
import com.example.rag.dto.QaChatMessage;
import com.example.rag.dto.QaSessionView;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 基于 Redis 的智能问答会话存储。
 *
 * <p>存储结构：
 * <ul>
 *     <li>rag:qa:sessions:{kbId}:{userId} -> 当前用户在该知识库下的会话 id 列表，最新会话在前</li>
 *     <li>rag:qa:session:meta:{sessionId} -> 会话摘要</li>
 *     <li>rag:qa:session:messages:{sessionId} -> 会话消息列表，按时间正序</li>
 *     <li>rag:qa:history:users:{kbId} -> 在该知识库下有会话的用户 id 集合</li>
 *     <li>rag:qa:history:sessions:{kbId} -> 该知识库下全部会话 id 集合，便于知识库删除时整体清理</li>
 * </ul>
 */
@Component
public class QaHistoryStore {

    private static final Logger log = LoggerFactory.getLogger(QaHistoryStore.class);

    private static final String SESSION_LIST_PREFIX = "rag:qa:sessions:";

    private static final String SESSION_META_PREFIX = "rag:qa:session:meta:";

    private static final String SESSION_MESSAGES_PREFIX = "rag:qa:session:messages:";

    private static final String USER_INDEX_PREFIX = "rag:qa:history:users:";

    private static final String SESSION_INDEX_PREFIX = "rag:qa:history:sessions:";

    /** 每个用户在每个知识库下最多保留的会话数 */
    private static final int MAX_SESSION_SIZE = 20;

    /** 每个会话最多保留的消息数；按“用户 + 助手”算，40 条即 20 轮 */
    private static final int MAX_MESSAGE_SIZE = 40;

    /** 历史记录的过期时间，避免长期不用的数据常驻 Redis */
    private static final Duration TTL = Duration.ofDays(30);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringRedisTemplate redisTemplate;

    /** 读取历史时忽略已废弃字段，避免旧结构遗留数据影响新逻辑 */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public QaHistoryStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 记录一轮完整问答，并返回最终所属的会话 id。
     */
    public String recordTurn(Long kbId, String sessionId, String question, AskResponse response) {
        if (kbId == null || !StringUtils.hasText(question) || response == null) {
            return sessionId;
        }
        Long userId;
        String username;
        try {
            userId = CurrentUser.id();
            username = CurrentUser.name();
        } catch (RuntimeException e) {
            log.warn("跳过问答会话记录：{}", e.getMessage());
            return sessionId;
        }

        String now = now();
        QaSessionView session = getOrCreateSession(kbId, userId, username, sessionId, question, now);
        if (session == null) {
            return sessionId;
        }
        try {
            appendMessage(userMessage(session.getId(), kbId, question, now));
            appendMessage(assistantMessage(session.getId(), kbId, response, now));
            trimMessages(session.getId());

            session.setPreview(abbreviate(question, 48));
            session.setMessageCount(countMessages(session.getId()));
            session.setUpdateTime(now);
            saveSession(session);
            touchSession(kbId, userId, session.getId());

            redisTemplate.opsForSet().add(USER_INDEX_PREFIX + kbId, String.valueOf(userId));
            redisTemplate.opsForSet().add(SESSION_INDEX_PREFIX + kbId, session.getId());
            redisTemplate.expire(userIndexKey(kbId), TTL);
            redisTemplate.expire(sessionIndexKey(kbId), TTL);
            return session.getId();
        } catch (Exception e) {
            log.warn("写入问答会话失败：kbId={}, sessionId={}, {}", kbId, session.getId(), e.getMessage());
            return session.getId();
        }
    }

    /** 当前用户在该知识库下的会话列表，最新会话在前 */
    public List<QaSessionView> listSessions(Long kbId) {
        Long userId = CurrentUser.id();
        List<String> ids = redisTemplate.opsForList().range(sessionListKey(kbId, userId), 0, -1);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<QaSessionView> sessions = new ArrayList<>(ids.size());
        for (String id : ids) {
            QaSessionView session = readSession(id);
            if (session != null && kbId.equals(session.getKbId()) && userId.equals(session.getUserId())) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    /** 当前用户查看指定会话的消息列表，按时间正序返回 */
    public List<QaChatMessage> listMessages(Long kbId, String sessionId) {
        QaSessionView session = requireSession(kbId, sessionId);
        List<String> values = redisTemplate.opsForList().range(sessionMessagesKey(session.getId()), 0, -1);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<QaChatMessage> messages = new ArrayList<>(values.size());
        for (String value : values) {
            QaChatMessage message = readMessage(value);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    /** 构造多轮上下文，只取当前会话最近若干轮消息 */
    public String buildHistoryContext(Long kbId, String sessionId, int rounds) {
        if (!StringUtils.hasText(sessionId) || rounds <= 0) {
            return "（暂无历史问答）";
        }
        List<QaChatMessage> messages;
        try {
            messages = listMessages(kbId, sessionId);
        } catch (IllegalArgumentException e) {
            return "（暂无历史问答）";
        }
        if (messages.isEmpty()) {
            return "（暂无历史问答）";
        }
        int fromIndex = Math.max(messages.size() - rounds * 2, 0);
        StringBuilder builder = new StringBuilder();
        for (QaChatMessage message : messages.subList(fromIndex, messages.size())) {
            builder.append(QaChatMessage.ROLE_USER.equals(message.getRole()) ? "Q: " : "A: ")
                    .append(defaultText(message.getContent(), ""))
                    .append("\n");
        }
        return builder.toString().trim();
    }

    /** 清空当前用户在该知识库下的全部会话 */
    public void clear(Long kbId) {
        Long userId = CurrentUser.id();
        List<String> sessionIds = redisTemplate.opsForList().range(sessionListKey(kbId, userId), 0, -1);
        if (sessionIds != null) {
            for (String sessionId : sessionIds) {
                redisTemplate.delete(sessionMetaKey(sessionId));
                redisTemplate.delete(sessionMessagesKey(sessionId));
                redisTemplate.opsForSet().remove(sessionIndexKey(kbId), sessionId);
            }
        }
        redisTemplate.delete(sessionListKey(kbId, userId));
        redisTemplate.opsForSet().remove(userIndexKey(kbId), String.valueOf(userId));
    }

    /** 知识库删除时清理其下所有用户会话 */
    public void clearAll(Long kbId) {
        String userIndex = userIndexKey(kbId);
        Set<String> userIds = redisTemplate.opsForSet().members(userIndex);
        if (userIds != null) {
            for (String userId : userIds) {
                redisTemplate.delete(SESSION_LIST_PREFIX + kbId + ":" + userId);
            }
        }
        String sessionIndex = sessionIndexKey(kbId);
        Set<String> sessionIds = redisTemplate.opsForSet().members(sessionIndex);
        if (sessionIds != null) {
            for (String sessionId : sessionIds) {
                redisTemplate.delete(sessionMetaKey(sessionId));
                redisTemplate.delete(sessionMessagesKey(sessionId));
            }
        }
        redisTemplate.delete(userIndex);
        redisTemplate.delete(sessionIndex);
    }

    private QaSessionView getOrCreateSession(Long kbId, Long userId, String username,
                                             String sessionId, String question, String now) {
        QaSessionView existing = StringUtils.hasText(sessionId) ? readSession(sessionId) : null;
        if (existing != null && kbId.equals(existing.getKbId()) && userId.equals(existing.getUserId())) {
            return existing;
        }
        QaSessionView created = new QaSessionView();
        created.setId(UUID.randomUUID().toString().replace("-", ""));
        created.setKbId(kbId);
        created.setUserId(userId);
        created.setUsername(username);
        created.setTitle(abbreviate(question, 24));
        created.setPreview(abbreviate(question, 48));
        created.setMessageCount(0);
        created.setCreateTime(now);
        created.setUpdateTime(now);
        saveSession(created);
        touchSession(kbId, userId, created.getId());
        return created;
    }

    private QaSessionView requireSession(Long kbId, String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("会话不存在");
        }
        QaSessionView session = readSession(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        Long userId = CurrentUser.id();
        if (!kbId.equals(session.getKbId()) || !userId.equals(session.getUserId())) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        return session;
    }

    private void appendMessage(QaChatMessage message) throws Exception {
        String key = sessionMessagesKey(message.getSessionId());
        redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(message));
        redisTemplate.expire(key, TTL);
    }

    private void trimMessages(String sessionId) {
        Long size = redisTemplate.opsForList().size(sessionMessagesKey(sessionId));
        if (size != null && size > MAX_MESSAGE_SIZE) {
            redisTemplate.opsForList().trim(sessionMessagesKey(sessionId), size - MAX_MESSAGE_SIZE, size - 1);
        }
    }

    private int countMessages(String sessionId) {
        Long size = redisTemplate.opsForList().size(sessionMessagesKey(sessionId));
        return size == null ? 0 : size.intValue();
    }

    private void saveSession(QaSessionView session) {
        try {
            redisTemplate.opsForValue().set(sessionMetaKey(session.getId()), objectMapper.writeValueAsString(session), TTL);
        } catch (Exception e) {
            throw new IllegalStateException("保存会话失败", e);
        }
    }

    private void touchSession(Long kbId, Long userId, String sessionId) {
        String key = sessionListKey(kbId, userId);
        redisTemplate.opsForList().remove(key, 0, sessionId);
        redisTemplate.opsForList().leftPush(key, sessionId);
        redisTemplate.opsForList().trim(key, 0, MAX_SESSION_SIZE - 1);
        redisTemplate.expire(key, TTL);
    }

    private QaChatMessage userMessage(String sessionId, Long kbId, String question, String now) {
        QaChatMessage message = new QaChatMessage();
        message.setId(UUID.randomUUID().toString().replace("-", ""));
        message.setSessionId(sessionId);
        message.setKbId(kbId);
        message.setRole(QaChatMessage.ROLE_USER);
        message.setContent(question);
        message.setCreateTime(now);
        return message;
    }

    private QaChatMessage assistantMessage(String sessionId, Long kbId, AskResponse response, String now) {
        QaChatMessage message = new QaChatMessage();
        message.setId(UUID.randomUUID().toString().replace("-", ""));
        message.setSessionId(sessionId);
        message.setKbId(kbId);
        message.setRole(QaChatMessage.ROLE_ASSISTANT);
        message.setContent(response.getAnswer());
        message.setSources(response.getSources());
        message.setSuggestions(response.getSuggestions());
        message.setCreateTime(now);
        return message;
    }

    private QaSessionView readSession(String sessionId) {
        String value = redisTemplate.opsForValue().get(sessionMetaKey(sessionId));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, QaSessionView.class);
        } catch (Exception e) {
            log.warn("解析会话摘要失败：{}", e.getMessage());
            return null;
        }
    }

    private QaChatMessage readMessage(String value) {
        try {
            return objectMapper.readValue(value, QaChatMessage.class);
        } catch (Exception e) {
            log.warn("解析会话消息失败：{}", e.getMessage());
            return null;
        }
    }

    private String sessionListKey(Long kbId, Long userId) {
        return SESSION_LIST_PREFIX + kbId + ":" + userId;
    }

    private String sessionMetaKey(String sessionId) {
        return SESSION_META_PREFIX + sessionId;
    }

    private String sessionMessagesKey(String sessionId) {
        return SESSION_MESSAGES_PREFIX + sessionId;
    }

    private String userIndexKey(Long kbId) {
        return USER_INDEX_PREFIX + kbId;
    }

    private String sessionIndexKey(Long kbId) {
        return SESSION_INDEX_PREFIX + kbId;
    }

    private String now() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }

    private String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private String defaultText(String text, String fallback) {
        return StringUtils.hasText(text) ? text : fallback;
    }
}

package com.example.rag.support;

import com.example.rag.dto.AskResponse;
import com.example.rag.dto.QaHistoryEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 基于 Redis 的智能问答历史维护，历史按“用户 + 知识库”维度隔离。
 *
 * <p>存储结构：
 * <ul>
 *     <li>rag:qa:history:{kbId}:{userId} -> 该用户在该知识库下的问答历史列表，最新在前</li>
 *     <li>rag:qa:history:users:{kbId} -> 在该知识库下提问过的用户 id 集合，便于知识库删除时整体清理</li>
 * </ul>
 */
@Component
public class QaHistoryStore {

    private static final Logger log = LoggerFactory.getLogger(QaHistoryStore.class);

    private static final String KEY_PREFIX = "rag:qa:history:";

    private static final String USER_INDEX_PREFIX = "rag:qa:history:users:";

    /** 每个用户在每个知识库下保留的历史条数 */
    private static final int MAX_SIZE = 20;

    /** 历史记录的过期时间，避免长期不用的数据常驻 Redis */
    private static final Duration TTL = Duration.ofDays(30);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public QaHistoryStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 记录一次问答，历史写入失败不影响问答结果本身
     */
    public void record(Long kbId, String question, AskResponse response) {
        if (kbId == null || response == null) {
            return;
        }
        Long userId;
        String username;
        try {
            userId = CurrentUser.id();
            username = CurrentUser.name();
        } catch (RuntimeException e) {
            log.warn("跳过问答历史记录：{}", e.getMessage());
            return;
        }

        QaHistoryEntry entry = new QaHistoryEntry();
        entry.setId(UUID.randomUUID().toString().replace("-", ""));
        entry.setKbId(kbId);
        entry.setUserId(userId);
        entry.setUsername(username);
        entry.setQuestion(question);
        entry.setAnswer(response.getAnswer());
        entry.setSources(response.getSources());
        entry.setCreateTime(LocalDateTime.now().format(TIME_FORMATTER));

        try {
            String key = key(kbId, userId);
            redisTemplate.opsForList().leftPush(key, objectMapper.writeValueAsString(entry));
            redisTemplate.opsForList().trim(key, 0, MAX_SIZE - 1);
            redisTemplate.expire(key, TTL);
            redisTemplate.opsForSet().add(USER_INDEX_PREFIX + kbId, String.valueOf(userId));
        } catch (Exception e) {
            log.warn("写入问答历史失败：kbId={}, {}", kbId, e.getMessage());
        }
    }

    /** 当前用户在该知识库下的问答历史，最新在前 */
    public List<QaHistoryEntry> list(Long kbId) {
        Long userId = CurrentUser.id();
        List<String> values = redisTemplate.opsForList().range(key(kbId, userId), 0, -1);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<QaHistoryEntry> entries = new ArrayList<>(values.size());
        for (String value : values) {
            QaHistoryEntry entry = read(value);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /** 清空当前用户在该知识库下的问答历史 */
    public void clear(Long kbId) {
        Long userId = CurrentUser.id();
        redisTemplate.delete(key(kbId, userId));
        redisTemplate.opsForSet().remove(USER_INDEX_PREFIX + kbId, String.valueOf(userId));
    }

    /** 知识库删除时清理其下所有用户的问答历史 */
    public void clearAll(Long kbId) {
        String indexKey = USER_INDEX_PREFIX + kbId;
        Set<String> userIds = redisTemplate.opsForSet().members(indexKey);
        if (userIds != null) {
            for (String userId : userIds) {
                redisTemplate.delete(KEY_PREFIX + kbId + ":" + userId);
            }
        }
        redisTemplate.delete(indexKey);
    }

    private String key(Long kbId, Long userId) {
        return KEY_PREFIX + kbId + ":" + userId;
    }

    private QaHistoryEntry read(String value) {
        try {
            return objectMapper.readValue(value, QaHistoryEntry.class);
        } catch (Exception e) {
            log.warn("解析问答历史失败：{}", e.getMessage());
            return null;
        }
    }
}

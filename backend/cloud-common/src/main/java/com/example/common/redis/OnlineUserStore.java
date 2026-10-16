package com.example.common.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis 的用户在线状态维护。
 *
 * <p>存储结构：
 * <ul>
 *     <li>auth:online:user:{userId} -> 在线用户 JSON，超时自动过期</li>
 *     <li>auth:online:users -> 在线用户 id 集合，用于快速列出在线用户</li>
 * </ul>
 */
@Component
public class OnlineUserStore {

    private static final Logger log = LoggerFactory.getLogger(OnlineUserStore.class);

    private static final String USER_KEY_PREFIX = "auth:online:user:";

    private static final String USER_ID_SET_KEY = "auth:online:users";

    /** 同一分钟内不重复写回活跃时间，减少 Redis 写入 */
    private static final long TOUCH_INTERVAL_SECONDS = 60L;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringRedisTemplate redisTemplate;

    private final Duration timeout;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OnlineUserStore(StringRedisTemplate redisTemplate,
                           @Value("${cloud.online.timeout:30m}") Duration timeout) {
        this.redisTemplate = redisTemplate;
        this.timeout = timeout;
    }

    /**
     * 登录成功后标记在线
     */
    public void online(OnlineUser user) {
        if (user == null || user.getUserId() == null) {
            return;
        }
        String now = now();
        if (user.getLoginTime() == null) {
            user.setLoginTime(now);
        }
        user.setLastActiveTime(now);
        redisTemplate.opsForValue().set(userKey(user.getUserId()), write(user), timeout);
        redisTemplate.opsForSet().add(USER_ID_SET_KEY, String.valueOf(user.getUserId()));
    }

    /**
     * 刷新活跃时间与过期时间，用户不在线时返回 false
     */
    public boolean touch(Long userId) {
        if (userId == null) {
            return false;
        }
        String key = userKey(userId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return false;
        }
        redisTemplate.expire(key, timeout);
        OnlineUser user = read(json);
        if (user != null && shouldRefreshActiveTime(user.getLastActiveTime())) {
            user.setLastActiveTime(now());
            redisTemplate.opsForValue().set(key, write(user), timeout);
        }
        return true;
    }

    /**
     * 登出后移除在线状态
     */
    public void offline(Long userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.delete(userKey(userId));
        redisTemplate.opsForSet().remove(USER_ID_SET_KEY, String.valueOf(userId));
    }

    public OnlineUser get(Long userId) {
        if (userId == null) {
            return null;
        }
        return read(redisTemplate.opsForValue().get(userKey(userId)));
    }

    /**
     * 列出当前在线用户，顺带清理集合中已过期的 id
     */
    public List<OnlineUser> listOnlineUsers() {
        Set<String> ids = redisTemplate.opsForSet().members(USER_ID_SET_KEY);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<OnlineUser> users = new ArrayList<>(ids.size());
        for (String id : ids) {
            OnlineUser user = get(parseUserId(id));
            if (user == null) {
                redisTemplate.opsForSet().remove(USER_ID_SET_KEY, id);
            } else {
                users.add(user);
            }
        }
        users.sort(Comparator.comparing(OnlineUser::getLoginTime,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return users;
    }

    public long count() {
        return listOnlineUsers().size();
    }

    private Long parseUserId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            log.warn("在线用户集合中存在非法 id：{}", id);
            return null;
        }
    }

    private boolean shouldRefreshActiveTime(String lastActiveTime) {
        if (lastActiveTime == null) {
            return true;
        }
        try {
            LocalDateTime last = LocalDateTime.parse(lastActiveTime, TIME_FORMATTER);
            return Duration.between(last, LocalDateTime.now()).getSeconds() >= TOUCH_INTERVAL_SECONDS;
        } catch (Exception e) {
            return true;
        }
    }

    private String userKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }

    private String now() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }

    private String write(OnlineUser user) {
        try {
            return objectMapper.writeValueAsString(user);
        } catch (Exception e) {
            throw new IllegalStateException("在线用户信息序列化失败", e);
        }
    }

    private OnlineUser read(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, OnlineUser.class);
        } catch (Exception e) {
            log.warn("在线用户信息反序列化失败：{}", json, e);
            return null;
        }
    }
}

package com.example.chat.ws;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间会话注册表：维护「房间 -> 在线连接」的映射。
 *
 * <p>一个连接同一时刻只属于一个房间，重复加入会自动退出上一个房间。
 * 所有集合均为并发容器，Netty 的多个 IO 线程可安全访问。
 */
@Component
public class ChatSessionRegistry {

    /** roomId -> (channelId -> 会话) */
    private final Map<Long, Map<String, ChatSession>> roomSessions = new ConcurrentHashMap<>();

    /** channelId -> roomId，连接断开时据此快速定位房间 */
    private final Map<String, Long> channelRooms = new ConcurrentHashMap<>();

    /**
     * 加入房间，返回加入后的在线人数
     */
    public int join(Long roomId, Channel channel, Long userId, String username) {
        // 先退出原房间，保证一个连接只在一个房间里
        leave(channel);
        String channelId = channelId(channel);
        roomSessions.computeIfAbsent(roomId, key -> new ConcurrentHashMap<>())
                .put(channelId, new ChatSession(channel, userId, username));
        channelRooms.put(channelId, roomId);
        return onlineCount(roomId);
    }

    /**
     * 退出房间，返回退出信息；该连接不在任何房间时返回 null
     */
    public LeaveResult leave(Channel channel) {
        String channelId = channelId(channel);
        Long roomId = channelRooms.remove(channelId);
        if (roomId == null) {
            return null;
        }
        Map<String, ChatSession> sessions = roomSessions.get(roomId);
        if (sessions == null) {
            return null;
        }
        ChatSession session = sessions.remove(channelId);
        // 条件删除，避免并发加入时误删掉新的会话表
        if (sessions.isEmpty()) {
            roomSessions.remove(roomId, sessions);
        }
        return session == null ? null
                : new LeaveResult(roomId, session.userId(), session.username(), onlineCount(roomId));
    }

    /**
     * 房间当前在线人数
     */
    public int onlineCount(Long roomId) {
        Map<String, ChatSession> sessions = roomSessions.get(roomId);
        return sessions == null ? 0 : sessions.size();
    }

    /**
     * 房间内的所有会话快照
     */
    public List<ChatSession> sessions(Long roomId) {
        Map<String, ChatSession> sessions = roomSessions.get(roomId);
        return sessions == null ? List.of() : new ArrayList<>(sessions.values());
    }

    /**
     * 连接当前所在房间，不在房间时返回 null
     */
    public Long roomIdOf(Channel channel) {
        return channelRooms.get(channelId(channel));
    }

    private String channelId(Channel channel) {
        return channel.id().asLongText();
    }

    /**
     * 一条在线会话
     */
    public record ChatSession(Channel channel, Long userId, String username) {
    }

    /**
     * 退出房间的结果
     */
    public record LeaveResult(Long roomId, Long userId, String username, int onlineCount) {
    }
}

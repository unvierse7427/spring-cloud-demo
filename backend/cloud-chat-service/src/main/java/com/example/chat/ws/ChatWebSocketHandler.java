package com.example.chat.ws;

import com.example.chat.config.ChatProperties;
import com.example.chat.entity.ChatMessage;
import com.example.chat.entity.ChatRoom;
import com.example.chat.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 聊天业务处理器：处理加入/离开房间、收发消息与心跳。
 *
 * <p>该处理器被挂载在独立的业务线程组上，数据库操作不会阻塞 Netty 的 IO 线程。
 *
 * <p>处理器本身无状态（会话数据都放在 {@link ChatSessionRegistry} 中），
 * 因此标记为 {@link ChannelHandler.Sharable}，可被所有连接共用一个实例。
 */
@Component
@ChannelHandler.Sharable
public class ChatWebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ChatService chatService;

    private final ChatSessionRegistry sessionRegistry;

    private final ObjectMapper objectMapper;

    private final ChatProperties chatProperties;

    public ChatWebSocketHandler(ChatService chatService,
                                ChatSessionRegistry sessionRegistry,
                                ObjectMapper objectMapper,
                                ChatProperties chatProperties) {
        this.chatService = chatService;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.chatProperties = chatProperties;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        JwtTokenVerifier.ChatUser user = ctx.channel().attr(HandshakeAuthHandler.USER_KEY).get();
        if (user == null) {
            // 握手阶段已强制校验，正常不会走到这里
            ctx.close();
            return;
        }

        WsMessage inbound;
        try {
            inbound = objectMapper.readValue(frame.text(), WsMessage.class);
        } catch (Exception e) {
            write(ctx, WsMessage.error("消息格式不正确，需为 JSON"));
            return;
        }
        if (inbound == null || inbound.getType() == null) {
            write(ctx, WsMessage.error("缺少消息类型"));
            return;
        }

        switch (inbound.getType()) {
            case WsMessage.JOIN:
                handleJoin(ctx, user, inbound);
                break;
            case WsMessage.LEAVE:
                handleLeave(ctx, user);
                break;
            case WsMessage.CHAT:
                handleChat(ctx, user, inbound);
                break;
            case WsMessage.PING:
                write(ctx, WsMessage.pong());
                break;
            default:
                write(ctx, WsMessage.error("不支持的消息类型：" + inbound.getType()));
        }
    }

    /**
     * 加入房间：回执带最近的历史消息，并向房间内其他成员广播系统消息
     */
    private void handleJoin(ChannelHandlerContext ctx, JwtTokenVerifier.ChatUser user, WsMessage inbound) {
        Long roomId = inbound.getRoomId();
        ChatRoom room = roomId == null ? null : chatService.getRoom(roomId);
        if (room == null) {
            write(ctx, WsMessage.error("房间不存在"));
            return;
        }

        int onlineCount = sessionRegistry.join(roomId, ctx.channel(), user.userId(), user.username());
        List<ChatMessage> history = chatService.latestMessages(roomId, chatProperties.getHistoryLimit());
        write(ctx, WsMessage.joined(roomId, onlineCount, history));
        write(ctx, WsMessage.system(roomId, onlineCount, user.username() + " 加入了房间"));
        broadcast(roomId, WsMessage.system(roomId, onlineCount, user.username() + " 加入了房间"),
                ctx.channel());
        log.debug("用户 {} 加入房间 {}，当前在线 {}", user.username(), roomId, onlineCount);
    }

    /**
     * 主动离开房间
     */
    private void handleLeave(ChannelHandlerContext ctx, JwtTokenVerifier.ChatUser user) {
        ChatSessionRegistry.LeaveResult result = sessionRegistry.leave(ctx.channel());
        if (result == null) {
            return;
        }
        write(ctx, WsMessage.left(result.roomId(), result.onlineCount()));
        broadcast(result.roomId(), WsMessage.system(result.roomId(), result.onlineCount(),
                result.username() + " 离开了房间"), null);
        log.debug("用户 {} 离开房间 {}，当前在线 {}", user.username(), result.roomId(), result.onlineCount());
    }

    /**
     * 转发聊天消息：先落库，再广播给房间内所有成员（含发送者自己）
     */
    private void handleChat(ChannelHandlerContext ctx, JwtTokenVerifier.ChatUser user, WsMessage inbound) {
        Long roomId = sessionRegistry.roomIdOf(ctx.channel());
        if (roomId == null) {
            write(ctx, WsMessage.error("尚未加入任何房间"));
            return;
        }
        try {
            ChatMessage saved = chatService.saveChatMessage(roomId, user.userId(), user.username(),
                    inbound.getContent());
            broadcast(roomId, WsMessage.chat(roomId, sessionRegistry.onlineCount(roomId), saved), null);
        } catch (IllegalArgumentException e) {
            write(ctx, WsMessage.error(e.getMessage()));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 浏览器关闭或网络断开时，把连接从房间中摘除并通知其他成员
        ChatSessionRegistry.LeaveResult result = sessionRegistry.leave(ctx.channel());
        if (result != null) {
            broadcast(result.roomId(), WsMessage.system(result.roomId(), result.onlineCount(),
                    result.username() + " 离开了房间"), null);
            log.debug("用户 {} 连接断开，房间 {} 当前在线 {}", result.username(), result.roomId(), result.onlineCount());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("WebSocket 连接异常：{}", cause.getMessage());
        ctx.close();
    }

    /**
     * 向单个连接发送一帧
     */
    private void write(ChannelHandlerContext ctx, WsMessage message) {
        String json = toJson(message);
        if (json != null) {
            ctx.writeAndFlush(new TextWebSocketFrame(json));
        }
    }

    /**
     * 向房间内所有成员广播一帧，可排除指定连接
     */
    private void broadcast(Long roomId, WsMessage message, io.netty.channel.Channel exclude) {
        String json = toJson(message);
        if (json == null) {
            return;
        }
        for (ChatSessionRegistry.ChatSession session : sessionRegistry.sessions(roomId)) {
            if (session.channel() != exclude && session.channel().isActive()) {
                // 每个连接单独构造帧，避免共用同一个引用计数对象
                session.channel().writeAndFlush(new TextWebSocketFrame(json));
            }
        }
    }

    private String toJson(WsMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("消息序列化失败", e);
            return null;
        }
    }
}

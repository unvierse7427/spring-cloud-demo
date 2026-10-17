package com.example.chat.ws;

import com.example.chat.entity.ChatMessage;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;

/**
 * WebSocket 通信帧。
 *
 * <p>客户端上行类型：JOIN / LEAVE / CHAT / PING；
 * 服务端下行类型：JOINED / LEFT / CHAT / SYSTEM / PONG / ERROR。
 * 字段按消息类型选择性填充，序列化时忽略 null。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    // ------------------------------ 客户端上行 ------------------------------

    /** 加入房间，需携带 roomId */
    public static final String JOIN = "JOIN";

    /** 离开房间 */
    public static final String LEAVE = "LEAVE";

    /** 发送聊天内容，需携带 roomId 与 content */
    public static final String CHAT = "CHAT";

    /** 心跳 */
    public static final String PING = "PING";

    // ------------------------------ 服务端下行 ------------------------------

    /** 加入成功，携带最近的历史消息 */
    public static final String JOINED = "JOINED";

    /** 已离开房间 */
    public static final String LEFT = "LEFT";

    /** 系统消息，如「xx 加入了房间」 */
    public static final String SYSTEM = "SYSTEM";

    /** 心跳响应 */
    public static final String PONG = "PONG";

    /** 处理失败 */
    public static final String ERROR = "ERROR";

    private String type;

    private Long roomId;

    /** 当前房间在线人数 */
    private Integer onlineCount;

    /** 单条消息，type 为 CHAT / SYSTEM 时存在 */
    private ChatMessage message;

    /** 历史消息，type 为 JOINED 时存在 */
    private List<ChatMessage> messages;

    /** 错误描述，type 为 ERROR 时存在 */
    private String content;

    public static WsMessage joined(Long roomId, int onlineCount, List<ChatMessage> messages) {
        WsMessage frame = new WsMessage();
        frame.setType(JOINED);
        frame.setRoomId(roomId);
        frame.setOnlineCount(onlineCount);
        frame.setMessages(messages);
        return frame;
    }

    public static WsMessage left(Long roomId, int onlineCount) {
        WsMessage frame = new WsMessage();
        frame.setType(LEFT);
        frame.setRoomId(roomId);
        frame.setOnlineCount(onlineCount);
        return frame;
    }

    public static WsMessage chat(Long roomId, int onlineCount, ChatMessage message) {
        WsMessage frame = new WsMessage();
        frame.setType(CHAT);
        frame.setRoomId(roomId);
        frame.setOnlineCount(onlineCount);
        frame.setMessage(message);
        return frame;
    }

    public static WsMessage system(Long roomId, int onlineCount, String content) {
        WsMessage frame = new WsMessage();
        frame.setType(SYSTEM);
        frame.setRoomId(roomId);
        frame.setOnlineCount(onlineCount);
        frame.setMessage(ChatMessage.system(roomId, content));
        return frame;
    }

    public static WsMessage pong() {
        WsMessage frame = new WsMessage();
        frame.setType(PONG);
        return frame;
    }

    public static WsMessage error(String content) {
        WsMessage frame = new WsMessage();
        frame.setType(ERROR);
        frame.setContent(content);
        return frame;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public Integer getOnlineCount() {
        return onlineCount;
    }

    public void setOnlineCount(Integer onlineCount) {
        this.onlineCount = onlineCount;
    }

    public ChatMessage getMessage() {
        return message;
    }

    public void setMessage(ChatMessage message) {
        this.message = message;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

package com.example.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天历史记录实体，对应表 chat_message。
 *
 * <p>只持久化用户消息，加入/离开房间这类系统消息仅实时广播，不写入历史记录。
 */
@TableName("chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户消息 */
    public static final String TYPE_CHAT = "CHAT";

    /** 系统消息 */
    public static final String TYPE_SYSTEM = "SYSTEM";

    /** 系统消息的发送者标识 */
    public static final String SYSTEM_USERNAME = "system";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private Long userId;

    private String username;

    private String content;

    /** CHAT 用户消息 / SYSTEM 系统消息 */
    private String msgType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 构造一条系统消息（不落库）
     */
    public static ChatMessage system(Long roomId, String content) {
        ChatMessage message = new ChatMessage();
        message.setRoomId(roomId);
        message.setUserId(0L);
        message.setUsername(SYSTEM_USERNAME);
        message.setContent(content);
        message.setMsgType(TYPE_SYSTEM);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}

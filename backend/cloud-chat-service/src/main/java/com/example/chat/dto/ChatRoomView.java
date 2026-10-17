package com.example.chat.dto;

import com.example.chat.entity.ChatRoom;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 房间视图：房间基础信息 + 当前在线人数（来自 Netty 会话注册表）
 */
public class ChatRoomView implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String description;

    private Long ownerId;

    private String ownerName;

    /** 当前在线人数 */
    private int onlineCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    public static ChatRoomView of(ChatRoom room, int onlineCount) {
        ChatRoomView view = new ChatRoomView();
        view.setId(room.getId());
        view.setName(room.getName());
        view.setDescription(room.getDescription());
        view.setOwnerId(room.getOwnerId());
        view.setOwnerName(room.getOwnerName());
        view.setOnlineCount(onlineCount);
        view.setCreateTime(room.getCreateTime());
        return view;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public int getOnlineCount() {
        return onlineCount;
    }

    public void setOnlineCount(int onlineCount) {
        this.onlineCount = onlineCount;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}

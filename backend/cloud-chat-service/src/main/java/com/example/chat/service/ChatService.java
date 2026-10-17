package com.example.chat.service;

import com.example.chat.entity.ChatMessage;
import com.example.chat.entity.ChatRoom;

import java.util.List;

/**
 * 聊天室业务接口：房间管理与历史记录读写
 */
public interface ChatService {

    /**
     * 房间列表，按创建顺序排列
     */
    List<ChatRoom> listRooms();

    /**
     * 按 id 查询房间，不存在返回 null
     */
    ChatRoom getRoom(Long roomId);

    /**
     * 创建房间
     */
    ChatRoom createRoom(String name, String description, Long ownerId, String ownerName);

    /**
     * 房间最近的历史消息，按时间正序返回
     */
    List<ChatMessage> latestMessages(Long roomId, int limit);

    /**
     * 保存一条用户聊天消息并返回落库后的实体
     */
    ChatMessage saveChatMessage(Long roomId, Long userId, String username, String content);
}

package com.example.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.chat.config.ChatProperties;
import com.example.chat.entity.ChatMessage;
import com.example.chat.entity.ChatRoom;
import com.example.chat.mapper.ChatMessageMapper;
import com.example.chat.mapper.ChatRoomMapper;
import com.example.chat.service.ChatService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 聊天室业务实现，基于 MyBatis-Plus 访问 chat_room 与 chat_message
 */
@Service
public class ChatServiceImpl implements ChatService {

    /** 房间名长度限制 */
    private static final int MIN_ROOM_NAME_LENGTH = 2;
    private static final int MAX_ROOM_NAME_LENGTH = 32;

    /** 历史消息单次查询上限，防止一次拉取过多数据 */
    private static final int MAX_HISTORY_LIMIT = 200;

    private final ChatRoomMapper chatRoomMapper;

    private final ChatMessageMapper chatMessageMapper;

    private final ChatProperties chatProperties;

    public ChatServiceImpl(ChatRoomMapper chatRoomMapper,
                           ChatMessageMapper chatMessageMapper,
                           ChatProperties chatProperties) {
        this.chatRoomMapper = chatRoomMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatProperties = chatProperties;
    }

    @Override
    public List<ChatRoom> listRooms() {
        return chatRoomMapper.selectList(new LambdaQueryWrapper<ChatRoom>().orderByAsc(ChatRoom::getId));
    }

    @Override
    public ChatRoom getRoom(Long roomId) {
        return roomId == null ? null : chatRoomMapper.selectById(roomId);
    }

    @Override
    public ChatRoom createRoom(String name, String description, Long ownerId, String ownerName) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("房间名称不能为空");
        }
        String roomName = name.trim();
        if (roomName.length() < MIN_ROOM_NAME_LENGTH || roomName.length() > MAX_ROOM_NAME_LENGTH) {
            throw new IllegalArgumentException("房间名称长度需在 " + MIN_ROOM_NAME_LENGTH + "-" + MAX_ROOM_NAME_LENGTH + " 个字符之间");
        }
        ChatRoom existing = chatRoomMapper.selectOne(new LambdaQueryWrapper<ChatRoom>()
                .eq(ChatRoom::getName, roomName)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new IllegalArgumentException("房间名称已存在：" + roomName);
        }

        ChatRoom room = new ChatRoom();
        room.setName(roomName);
        room.setDescription(StringUtils.hasText(description) ? description.trim() : null);
        room.setOwnerId(ownerId);
        room.setOwnerName(ownerName);
        chatRoomMapper.insert(room);
        return chatRoomMapper.selectById(room.getId());
    }

    @Override
    public List<ChatMessage> latestMessages(Long roomId, int limit) {
        int safeLimit = limit <= 0 ? chatProperties.getHistoryLimit()
                : Math.min(limit, MAX_HISTORY_LIMIT);
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRoomId, roomId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + safeLimit));
        // 查询按 id 倒序取最近若干条，返回前恢复为时间正序，便于前端直接渲染
        Collections.reverse(messages);
        return messages;
    }

    @Override
    public ChatMessage saveChatMessage(Long roomId, Long userId, String username, String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        String text = content.trim();
        int maxLength = chatProperties.getMaxContentLength();
        if (text.length() > maxLength) {
            throw new IllegalArgumentException("单条消息不能超过 " + maxLength + " 个字符");
        }

        ChatMessage message = new ChatMessage();
        message.setRoomId(roomId);
        message.setUserId(userId);
        message.setUsername(username);
        message.setContent(text);
        message.setMsgType(ChatMessage.TYPE_CHAT);
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);
        return message;
    }
}

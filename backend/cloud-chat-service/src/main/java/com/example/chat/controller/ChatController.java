package com.example.chat.controller;

import com.example.chat.dto.ChatRoomView;
import com.example.chat.dto.CreateRoomRequest;
import com.example.chat.entity.ChatMessage;
import com.example.chat.entity.ChatRoom;
import com.example.chat.service.ChatService;
import com.example.chat.ws.ChatSessionRegistry;
import com.example.common.result.Result;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 聊天室 HTTP 接口
 *
 * <p>对外访问路径：网关 /api/chat/** -> 本服务 /chat/**。
 * 实时消息收发由 Netty WebSocket 承担，这里只负责房间与历史记录。
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    private final ChatSessionRegistry sessionRegistry;

    public ChatController(ChatService chatService, ChatSessionRegistry sessionRegistry) {
        this.chatService = chatService;
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * 房间列表，附带各自当前的在线人数
     */
    @GetMapping("/rooms")
    public Result<List<ChatRoomView>> rooms() {
        List<ChatRoomView> views = chatService.listRooms().stream()
                .map(room -> ChatRoomView.of(room, sessionRegistry.onlineCount(room.getId())))
                .collect(Collectors.toList());
        return Result.success(views);
    }

    /**
     * 创建房间，创建者取自当前登录令牌
     */
    @PostMapping("/rooms")
    public Result<ChatRoomView> createRoom(@RequestBody CreateRoomRequest request, Authentication authentication) {
        Jwt jwt = currentJwt(authentication);
        ChatRoom room = chatService.createRoom(request.getName(), request.getDescription(),
                Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("username"));
        return Result.success("房间创建成功", ChatRoomView.of(room, sessionRegistry.onlineCount(room.getId())));
    }

    /**
     * 房间历史消息（时间正序）
     */
    @GetMapping("/rooms/{roomId}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable Long roomId,
                                              @RequestParam(defaultValue = "50") int limit) {
        if (chatService.getRoom(roomId) == null) {
            return Result.fail(404, "房间不存在：" + roomId);
        }
        return Result.success(chatService.latestMessages(roomId, limit));
    }

    private Jwt currentJwt(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            throw new IllegalStateException("未获取到当前登录用户");
        }
        return (Jwt) authentication.getPrincipal();
    }
}

package com.ecommerce.api.chatroom.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.chatroom.dto.ChatRoomListRes;
import com.ecommerce.api.chatroom.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/chat-rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @GetMapping
    public ResponseEntity<List<ChatRoomListRes>> findChatRoomList(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(chatRoomService.findChatRoomList(userDetails.getUserId(), userDetails.getUserRole()));
    }
}

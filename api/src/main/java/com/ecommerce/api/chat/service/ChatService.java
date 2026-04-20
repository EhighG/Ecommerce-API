package com.ecommerce.api.chat.service;

import com.ecommerce.api.chat.dto.ChatListRes;
import com.ecommerce.api.chat.dto.ModifyChatReq;
import com.ecommerce.api.chat.dto.SearchReq;
import com.ecommerce.api.chat.dto.SendChatReq;
import com.ecommerce.api.chat.entity.Chat;
import com.ecommerce.api.chat.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ChatService {

    private final ChatRepository chatRepository;

    public Long send(SendChatReq req, Long senderId) {
        return null;
    }

    public List<ChatListRes> search(SearchReq req) {
        return null;
    }

    public Chat findLastChatOf(Long chatRoomId) {
        return null;
    }

    public void modify(ModifyChatReq req, Long userId) {

    }

    public void delete(Long chatId, Long userId) {

    }
}

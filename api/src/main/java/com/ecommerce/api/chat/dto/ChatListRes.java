package com.ecommerce.api.chat.dto;

import java.time.Instant;

public record ChatListRes(
        Long chatId,
        String content,
        boolean modified,
        Instant createdAt,
        Instant updatedAt
        // 1:1 채팅만 있을 땐 필요 X
//        Long senderId,
//        String send
) {
}

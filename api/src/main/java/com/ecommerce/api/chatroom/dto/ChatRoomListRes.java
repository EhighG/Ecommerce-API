package com.ecommerce.api.chatroom.dto;

import com.ecommerce.api.chat.entity.Chat;
import com.ecommerce.api.chatroom.entity.ChatRoom;
import com.ecommerce.api.product.dto.ProductSummary;
import com.ecommerce.api.user.entity.User;

import java.time.Instant;

public record ChatRoomListRes(
        Long chatRoomId,
        ProductSummary product,
        boolean productDeleted,
        UserSummary otherUser,
        ChatSummary lastChat
) {
    public static ChatRoomListRes withValidProduct(ChatRoom chatRoom, User otherUser, Chat lastChat,
                                                   String productThumbnailUrl) {
        return new ChatRoomListRes(
                chatRoom.getId(),
                new ProductSummary(chatRoom.getProduct(), productThumbnailUrl),
                false,
                new UserSummary(otherUser),
                new ChatSummary(lastChat)
        );
    }

    public static ChatRoomListRes withDeletedProduct(ChatRoom chatRoom, User otherUser, Chat lastChat) {
        return new ChatRoomListRes(
                chatRoom.getId(),
                null,
                true,
                new UserSummary(otherUser),
                new ChatSummary(lastChat)
        );
    }

    public record UserSummary(
            Long userId,
            String nickname
    ) {
        public UserSummary(User user) {
            this(user.getId(), user.getNickname());
        }
    }

    public record ChatSummary(
            String content,
            Instant createdAt
    ) {
        public ChatSummary(Chat chat) {
            this(chat.getContent(), chat.getCreatedAt());
        }
    }
}

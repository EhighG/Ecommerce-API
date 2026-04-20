package com.ecommerce.api.chatroom.dto;

import com.ecommerce.api.chatroom.entity.ChatRoom;
import com.ecommerce.api.product.dto.ProductSummary;
import com.ecommerce.api.user.entity.User;

import java.time.Instant;

public record ChatRoomDetailRes(
        Long chatRoomId,
        UserSummary otherUser,
        ProductSummary product,
        Instant createdAt
) {
    public ChatRoomDetailRes(ChatRoom chatRoom, User otherUser, boolean otherUserLeft, String productThumbnailUrl) {
        this(
                chatRoom.getId(),
                new UserSummary(otherUser, otherUserLeft),
                new ProductSummary(chatRoom.getProduct(), productThumbnailUrl),
                chatRoom.getCreatedAt()
        );
    }

    public record UserSummary(
            Long userId,
            String nickname,
            boolean left
    ) {
        public UserSummary(User user, boolean left) {
            this(user.getId(), user.getNickname(), left);
        }
    }
}

package com.ecommerce.api.chatroom.dto;

import jakarta.validation.constraints.NotNull;

public record CreateChatRoomReq(
        @NotNull Long productId
) {}

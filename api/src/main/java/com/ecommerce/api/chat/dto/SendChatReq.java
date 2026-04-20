package com.ecommerce.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendChatReq(
        @NotNull Long chatRoomId,
        @NotBlank String content
) {
}

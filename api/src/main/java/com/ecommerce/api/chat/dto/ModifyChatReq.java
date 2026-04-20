package com.ecommerce.api.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ModifyChatReq(
        @NotNull Long chatId,
        @NotBlank String content
) {
}

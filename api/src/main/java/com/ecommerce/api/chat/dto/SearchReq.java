package com.ecommerce.api.chat.dto;

import jakarta.validation.constraints.NotNull;

public record SearchReq(
        String keyword,
        @NotNull Long chatRoomId,
        Integer page
        // 사이즈는 고정
) {
    public SearchReq {
        if (page == null) page = 1;
    }
}

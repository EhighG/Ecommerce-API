package com.ecommerce.api.user.dto;

import com.ecommerce.api.user.entity.User;

public record UserSummary(
        Long id,
        String nickname
) {
    public UserSummary(User user) {
        this(user.getId(), user.getNickname());
    }
}

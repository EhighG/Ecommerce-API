package com.ecommerce.api.user.dto;

import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.enums.UserRole;

public record UserInfoListRes(
        Long id,
        String nickname,
        String email,
        UserRole role,
        boolean deleted
) {
    public UserInfoListRes(User user) {
        this(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getRole(),
                user.isDeleted()
        );
    }
}

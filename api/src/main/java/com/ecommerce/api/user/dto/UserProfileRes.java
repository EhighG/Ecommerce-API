package com.ecommerce.api.user.dto;

import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.enums.UserRole;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserProfileRes(
        Long userId,
        String email,
        String nickname,
        UserRole role,
        LocalDate joinDate,
        ReviewSection reviewSection
) {
    public UserProfileRes(User user) {
        this(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
                null
        );
    }

    public UserProfileRes(User user, long totalReviewCount, List<UserReviewListRes> reviewList) {
        this(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole(),
                user.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
                new ReviewSection(totalReviewCount, reviewList)
        );
    }

    public record ReviewSection(
            long totalCount,
            List<UserReviewListRes> reviewList
    ) {
    }
}

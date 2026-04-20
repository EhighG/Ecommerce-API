package com.ecommerce.api.review.dto;

import com.ecommerce.api.review.entity.Review;
import com.ecommerce.api.user.entity.User;

import java.time.Instant;

public record ProductReviewListRes(
        Long reviewId,
        UserSummary writer,
        int halfStars,
        String content,
        Instant lastUpdatedAt,
        boolean isUpdated
) {
    public static ProductReviewListRes from(Review review) {
        Instant createdAt = review.getCreatedAt();
        Instant updatedAt = review.getUpdatedAt();
        boolean isModified = !updatedAt.equals(createdAt);

        return new ProductReviewListRes(
                review.getId(),
                new UserSummary(review.getWriter()),
                review.getRating().getHalfStars(),
                review.getContent(),
                updatedAt,
                isModified
        );
    }

    public record UserSummary(
            Long userId,
            String nickname
    ) {
        public UserSummary(User user) {
            this(user.getId(), user.isDeleted() ? "삭제된 사용자입니다" : user.getNickname());
        }
    }
}

package com.ecommerce.api.user.dto;

import com.ecommerce.api.product.dto.ProductSummary;
import com.ecommerce.api.review.entity.Review;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserReviewListRes(
        Long reviewId,
        ProductSummary product,
        int halfStars,
        String content,
        Instant lastUpdatedAt,
        boolean isUpdated
) {
    public static UserReviewListRes of(Review review, String productThumbnailUrl) {
        Instant createdAt = review.getCreatedAt();
        Instant updatedAt = review.getUpdatedAt();
        boolean isModified = !updatedAt.equals(createdAt);

        return new UserReviewListRes(
                review.getId(),
                new ProductSummary(review.getProduct(), productThumbnailUrl),
                review.getRating().getHalfStars(),
                review.getContent(),
                updatedAt,
                isModified
        );
    }
}

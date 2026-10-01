package com.ecommerce.api.user.dto;

import com.ecommerce.api.product.dto.ProductSummary;
import com.ecommerce.api.review.entity.Rating;
import com.ecommerce.api.review.entity.Review;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserReviewListRes(
        Long reviewId,
        ProductSummary product,
        @Schema(description = Rating.HALF_STARS_DESCRIPTION) int halfStars,
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

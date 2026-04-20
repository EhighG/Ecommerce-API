package com.ecommerce.api.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModifyReviewReq(
        Long reviewId,
        @NotNull @Min(0) @Max(10) Integer halfStars,
        // content == null이면, 빈 내용으로 교체
        @Size(max = 255) String content
) {
    public ModifyReviewReq withReviewId(Long reviewId) {
        return new ModifyReviewReq(reviewId, halfStars, content);
    }
}

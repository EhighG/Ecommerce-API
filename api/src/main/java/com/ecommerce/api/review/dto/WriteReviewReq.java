package com.ecommerce.api.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WriteReviewReq(
        Long productId,
        @NotNull @Min(0) @Max(10) Integer halfStars,
        @Size(max = 255) String content
) {
    public WriteReviewReq withProductId(Long productId) {
        return new WriteReviewReq(productId, halfStars, content);
    }
}

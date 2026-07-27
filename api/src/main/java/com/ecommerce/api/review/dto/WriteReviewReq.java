package com.ecommerce.api.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WriteReviewReq(
        Long productId,
        @NotNull(message = "별점은 필수입니다.") @Min(value = 0, message = "별점은 0점 이상 5점 이하로 입력해야 합니다.") @Max(value = 10, message = "별점은 0점 이상 5점 이하로 입력해야 합니다.") Integer halfStars,
        @Size(max = 255, message = "리뷰 내용은 255자 이하여야 합니다.") String content
) {
    public WriteReviewReq withProductId(Long productId) {
        return new WriteReviewReq(productId, halfStars, content);
    }
}

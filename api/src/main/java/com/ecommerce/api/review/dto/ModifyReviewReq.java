package com.ecommerce.api.review.dto;

import com.ecommerce.api.review.entity.Rating;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModifyReviewReq(
        @Schema(hidden = true) Long reviewId, // 경로의 값으로 채운다
        @Schema(description = Rating.HALF_STARS_DESCRIPTION) @NotNull(message = "별점은 필수입니다.") @Min(value = 0, message = "별점은 0점 이상 5점 이하로 입력해야 합니다.") @Max(value = 10, message = "별점은 0점 이상 5점 이하로 입력해야 합니다.") Integer halfStars,
        // content == null이면, 빈 내용으로 교체
        @Size(max = 255, message = "리뷰 내용은 255자 이하여야 합니다.") String content
) {
    public ModifyReviewReq withReviewId(Long reviewId) {
        return new ModifyReviewReq(reviewId, halfStars, content);
    }
}

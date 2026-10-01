package com.ecommerce.api.review.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.common.openapi.ApiErrorCode;
import com.ecommerce.api.review.dto.ModifyReviewReq;
import com.ecommerce.api.review.dto.ReviewSearchRes;
import com.ecommerce.api.review.dto.SearchReq;
import com.ecommerce.api.review.dto.WriteReviewReq;
import com.ecommerce.api.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@Tag(name = "Review", description = "리뷰")
@RequiredArgsConstructor
@RestController
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "리뷰 작성", description = "응답은 리뷰 ID다.")
    @ApiErrorCode(value = NO_CONFIRMED_ORDER_EXISTS, when = "이 상품을 구매확정한 주문항목이 없음")
    @ApiErrorCode(value = REVIEW_ALREADY_EXISTS, when = "이미 이 상품에 리뷰를 씀")
    @ApiErrorCode(value = PRODUCT_NOT_FOUND, when = "없거나 삭제된 상품")
    @PostMapping("/products/{productId}/reviews")
    public ResponseEntity<Long> write(@PathVariable Long productId,
                                      @Valid @RequestBody WriteReviewReq req,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(reviewService.write(req.withProductId(productId), userDetails.getUserId()));
    }

    @Operation(summary = "리뷰 목록", description = "`searchBy`가 `PRODUCT`이면 상품 기준, `WRITER`이면 작성자 기준으로 조회한다.")
    @ApiErrorCode(value = PRODUCT_NOT_FOUND, when = "없거나 삭제된 상품")
    @ApiErrorCode(value = USER_NOT_FOUND, when = "없거나 탈퇴한 작성자")
    @GetMapping("/reviews")
    public ResponseEntity<ReviewSearchRes> search(@ParameterObject @Valid @ModelAttribute SearchReq req) {
        return ResponseEntity.ok(reviewService.search(req));
    }

    @Operation(summary = "리뷰 수정", description = "별점과 내용을 통째로 바꾼다.")
    @ApiErrorCode(value = DELETED_PRODUCT, when = "삭제된 상품의 리뷰")
    @ApiErrorCode(value = REVIEW_NOT_FOUND, when = "리뷰가 없음")
    @ApiErrorCode(value = REVIEW_WRITER_MISMATCH, when = "남의 리뷰")
    @PatchMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> modify(@PathVariable Long reviewId,
                                       @Valid @RequestBody ModifyReviewReq req,
                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        reviewService.modifyReview(req.withReviewId(reviewId), userDetails.getUserId());
        return ResponseEntity.ok().build();
    }
}

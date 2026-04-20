package com.ecommerce.api.review.controller;

import com.ecommerce.api.auth.domain.CustomUserDetails;
import com.ecommerce.api.review.dto.ModifyReviewReq;
import com.ecommerce.api.review.dto.ReviewSearchRes;
import com.ecommerce.api.review.dto.SearchReq;
import com.ecommerce.api.review.dto.WriteReviewReq;
import com.ecommerce.api.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/products/{productId}/reviews")
    public ResponseEntity<Long> write(@PathVariable Long productId,
                                      @Valid @RequestBody WriteReviewReq req,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity
                .ok(reviewService.write(req.withProductId(productId), userDetails.getUserId()));
    }

    @GetMapping("/reviews")
    public ResponseEntity<ReviewSearchRes> search(@Valid @ModelAttribute SearchReq req) {
        return ResponseEntity.ok(reviewService.search(req));
    }

    @PatchMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> modify(@PathVariable Long reviewId,
                                       @Valid @RequestBody ModifyReviewReq req,
                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        reviewService.modifyReview(req.withReviewId(reviewId), userDetails.getUserId());
        return ResponseEntity.ok().build();
    }
}

package com.ecommerce.api.review.dto;

import com.ecommerce.api.user.dto.UserReviewListRes;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.domain.Page;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewSearchRes(
        List<ProductReviewListRes> productReviews,
        List<UserReviewListRes> userReviews,
        int page,
        int size,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
    public static ReviewSearchRes forProduct(Page<ProductReviewListRes> page) {
        return new ReviewSearchRes(page.getContent(), null, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    public static ReviewSearchRes forUser(Page<UserReviewListRes> page) {
        return new ReviewSearchRes(null, page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}

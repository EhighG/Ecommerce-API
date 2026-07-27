package com.ecommerce.api.review.dto;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SearchReq(
        @NotNull(message = "리뷰 검색 기준은 필수입니다.") SearchBy searchBy,
        Long productId,
        Long writerId,
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") Integer size
) {
    public SearchReq {
        if ((searchBy == SearchBy.PRODUCT && productId == null) ||
                (searchBy == SearchBy.WRITER && writerId == null)) {
            throw new AppException(ErrorCode.INVALID_INPUT, "검색 기준에 맞는 상품 ID 또는 작성자 ID가 필요합니다.");
        }

        if (page == null) page = 0;
        if (size == null) size = 20;
    }

    public enum SearchBy {
        PRODUCT, WRITER
    }
}

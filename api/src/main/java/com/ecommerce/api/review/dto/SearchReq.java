package com.ecommerce.api.review.dto;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SearchReq(
        @NotNull SearchBy searchBy,
        Long productId,
        Long writerId,
        @Min(0) Integer page,
        @Min(1) Integer size
) {
    public SearchReq {
        if ((searchBy == SearchBy.PRODUCT && productId == null) ||
                (searchBy == SearchBy.WRITER && writerId == null)) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Exactly one of Product Id or Writer Id is required");
        }

        if (page == null) page = 0;
        if (size == null) size = 20;
    }

    public enum SearchBy {
        PRODUCT, WRITER
    }
}

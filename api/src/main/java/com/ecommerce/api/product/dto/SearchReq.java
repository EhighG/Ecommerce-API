package com.ecommerce.api.product.dto;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.product.enums.SortType;
import jakarta.validation.constraints.Min;

public record SearchReq(
        String keyword,
        Long categoryId,
        Long sellerId,
        @Min(0) Integer page,
        @Min(1) Integer size,
        SortType sortBy,
        SortDirection direction
) {
    public SearchReq {
        if (page == null) page = 0;
        if (size == null) size = 20;
        if (!(size == 20 || size == 50 || size == 100))
            throw new AppException(ErrorCode.INVALID_INPUT, "페이지 크기는 20, 50, 100 중 하나여야 합니다.");

        if (sortBy == null) sortBy = SortType.REG_DATE;
        if (direction == null) direction = SortDirection.DESC;
    }

    public enum SortDirection {
        ASC, DESC
    }
}

package com.ecommerce.api.product.dto;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import com.ecommerce.api.product.enums.SortType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

public record SearchReq(
        String keyword,
        Long categoryId,
        Long sellerId,
        @Schema(defaultValue = "0") @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.") Integer page,
        @Schema(defaultValue = "20", allowableValues = {"20", "50", "100"}) @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") Integer size,
        @Schema(defaultValue = "REG_DATE") SortType sortBy,
        @Schema(defaultValue = "DESC") SortDirection direction
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

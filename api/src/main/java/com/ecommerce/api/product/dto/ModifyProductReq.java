package com.ecommerce.api.product.dto;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ModifyProductReq(
        @Size(max = 1000, message = "상품 설명은 1000자 이하여야 합니다.") String description,
        @Min(value = 0, message = "상품 가격은 0원 이상이어야 합니다.") Long unitPrice
) {
    public ModifyProductReq {
        if (description == null && unitPrice == null)
            throw new AppException(ErrorCode.INVALID_INPUT);
        if (description != null && description.isBlank())
            throw new AppException(ErrorCode.INVALID_INPUT);
    }
}

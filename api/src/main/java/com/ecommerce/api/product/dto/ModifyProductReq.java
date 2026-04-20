package com.ecommerce.api.product.dto;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.exception.ErrorCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ModifyProductReq(
        @Size(max = 1000) String description,
        @Min(0) Long unitPrice
) {
    public ModifyProductReq {
        if (description == null && unitPrice == null)
            throw new AppException(ErrorCode.INVALID_INPUT);
        if (description != null && description.isBlank())
            throw new AppException(ErrorCode.INVALID_INPUT);
    }
}

package com.ecommerce.api.common.api;

import com.ecommerce.api.common.exception.ErrorCode;

public record ApiError(
        String code,
        String message
) {
    public ApiError(ErrorCode errorCode) {
        this(errorCode.code(), errorCode.message());
    }
}

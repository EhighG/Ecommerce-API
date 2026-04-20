package com.ecommerce.api.common.websocket;

import com.ecommerce.api.common.exception.ErrorCode;

public record WsError(
        int code,
        String message
) {
    public WsError(ErrorCode errorCode) {
        this(errorCode.code(), errorCode.message());
    }

    public WsError(ErrorCode errorCode, String message) {
        this(errorCode.code(), message);
    }
}

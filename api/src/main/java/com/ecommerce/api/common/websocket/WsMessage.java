package com.ecommerce.api.common.websocket;

import com.ecommerce.api.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WsMessage<T>(
        WsMessageType type,
        T data,
        WsError error,
        Instant sentAt
) {
    public static <T> WsMessage<T> of(WsMessageType type, T data) {
        return new WsMessage<>(type, data, null, Instant.now());
    }

    public static WsMessage<Void> error(ErrorCode errorCode) {
        return new WsMessage<>(WsMessageType.ERROR, null, new WsError(errorCode), Instant.now());
    }

    public static WsMessage<Void> error(ErrorCode errorCode, String message) {
        return new WsMessage<>(WsMessageType.ERROR, null, new WsError(errorCode.code(), message), Instant.now());
    }
}

package com.ecommerce.api.common.openapi;

import com.ecommerce.api.common.exception.ErrorCode;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 클라이언트가 분기할 오류 응답 하나. HTTP 상태, 코드, 기본 메시지는 {@link ErrorCode}에서 가져와 명세에 넣는다.
 * 분기할 오류가 없는 엔드포인트는 {@code @ApiErrorCodes({})}를 단다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiErrorCodes.class)
public @interface ApiErrorCode {

    ErrorCode value();

    /**
     * 이 코드가 나는 조건. 비우면 기본 메시지를 조건으로 쓴다.
     */
    String when() default "";
}

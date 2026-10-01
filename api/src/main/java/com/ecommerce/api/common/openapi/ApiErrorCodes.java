package com.ecommerce.api.common.openapi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link ApiErrorCode}를 묶는 어노테이션. 분기할 오류가 없다는 것도 {@code @ApiErrorCodes({})}로 명시한다.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodes {

    ApiErrorCode[] value();
}

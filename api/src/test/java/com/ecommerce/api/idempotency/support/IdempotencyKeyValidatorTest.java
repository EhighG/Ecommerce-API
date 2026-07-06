package com.ecommerce.api.idempotency.support;

import com.ecommerce.api.common.exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.ecommerce.api.common.exception.ErrorCode.IDEMPOTENCY_KEY_INVALID;
import static com.ecommerce.api.common.exception.ErrorCode.IDEMPOTENCY_KEY_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdempotencyKeyValidatorTest {

    private final IdempotencyKeyValidator validator = new IdempotencyKeyValidator();

    @Test
    @DisplayName("멱등성 키가 없으면 필수 키 오류를 반환한다")
    void givenNullKey_whenValidate_thenThrowRequiredError() {
        // given
        String idempotencyKey = null;

        // when
        AppException exception = assertThrows(AppException.class,
                () -> validator.validate(idempotencyKey));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(IDEMPOTENCY_KEY_REQUIRED);
    }

    @ParameterizedTest
    @MethodSource("invalidIdempotencyKeys")
    @DisplayName("멱등성 키 형식이 잘못되면 형식 오류를 반환한다")
    void givenInvalidKey_whenValidate_thenThrowInvalidKeyError(String idempotencyKey) {
        // when
        AppException exception = assertThrows(AppException.class,
                () -> validator.validate(idempotencyKey));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(IDEMPOTENCY_KEY_INVALID);
    }

    @ParameterizedTest
    @MethodSource("validIdempotencyKeys")
    @DisplayName("멱등성 키 형식이 올바르면 통과한다")
    void givenValidKey_whenValidate_thenDoesNotThrow(String idempotencyKey) {
        assertDoesNotThrow(() -> validator.validate(idempotencyKey));
    }

    private static Stream<String> invalidIdempotencyKeys() {
        return Stream.of(
                "",
                " ",
                "invalid key",
                "invalid#key",
                "a".repeat(129)
        );
    }

    private static Stream<String> validIdempotencyKeys() {
        return Stream.of(
                "order-create-1",
                "order.create:1_2",
                "a".repeat(128)
        );
    }
}

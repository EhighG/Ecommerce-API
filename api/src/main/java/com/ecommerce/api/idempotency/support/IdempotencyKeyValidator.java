package com.ecommerce.api.idempotency.support;

import com.ecommerce.api.common.exception.AppException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

import static com.ecommerce.api.common.exception.ErrorCode.IDEMPOTENCY_KEY_INVALID;
import static com.ecommerce.api.common.exception.ErrorCode.IDEMPOTENCY_KEY_REQUIRED;

@Component
public class IdempotencyKeyValidator {

    private static final int MAX_LENGTH = 128;
    private static final Pattern ALLOWED_PATTERN =
            Pattern.compile("^[A-Za-z0-9._:-]+$");

    public void validate(String idempotencyKey) {
        if (idempotencyKey == null) {
            throw new AppException(IDEMPOTENCY_KEY_REQUIRED);
        }

        if (idempotencyKey.isBlank()
                || idempotencyKey.length() > MAX_LENGTH
                || !ALLOWED_PATTERN.matcher(idempotencyKey).matches()) {
            throw new AppException(IDEMPOTENCY_KEY_INVALID);
        }
    }
}

package com.ecommerce.api.idempotency.support;

public interface RequestFingerprintGenerator<T> {
    String generate(T payload);
}

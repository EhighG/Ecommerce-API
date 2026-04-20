package com.ecommerce.api.media.storage;

public record StoredObject(
        String objectKey,
        String contentType,
        long fileSize
) {
}

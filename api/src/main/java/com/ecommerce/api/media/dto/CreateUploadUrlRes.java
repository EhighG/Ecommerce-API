package com.ecommerce.api.media.dto;

import com.ecommerce.api.media.storage.SignedUploadUrl;

import java.time.Instant;

public record CreateUploadUrlRes(
        String objectKey,
        String uploadUrl,
        Instant expiresAt
) {
    public static CreateUploadUrlRes from(SignedUploadUrl signedUploadUrl) {
        return new CreateUploadUrlRes(
                signedUploadUrl.objectKey(),
                signedUploadUrl.uploadUrl(),
                signedUploadUrl.expiresAt()
        );
    }
}

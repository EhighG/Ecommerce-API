package com.ecommerce.api.media.storage;

import java.time.Instant;

public record SignedUploadUrl(
        String objectKey,
        String uploadUrl,
        Instant expiresAt
) {}

package com.ecommerce.api.media.storage;

import java.time.Duration;
import java.util.Optional;

public interface ObjectStorageClient {
    SignedUploadUrl createSignedUploadUrl(String objectKey, String contentType, Duration duration);
    Optional<StoredObject> getObject(String objectKey);
    String getPublicUrl(String objectKey);
    boolean exists(String objectKey);
    void delete(String objectKey);
}

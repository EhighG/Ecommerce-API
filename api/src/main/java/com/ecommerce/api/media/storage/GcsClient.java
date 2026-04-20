package com.ecommerce.api.media.storage;

import com.ecommerce.api.common.config.StorageProperties;
import com.google.cloud.storage.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class GcsClient implements ObjectStorageClient {

    private final Storage storage;
    private final StorageProperties storageProperties;

    @Override
    public SignedUploadUrl createSignedUploadUrl(String objectKey, String contentType, Duration duration) {
        BlobInfo blobInfo = BlobInfo.newBuilder(
                BlobId.of(storageProperties.gcs().bucket(), objectKey)
        ).setContentType(contentType).build();

        URL signedUrl = storage.signUrl(
                blobInfo,
                duration.toSeconds(),
                TimeUnit.SECONDS,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.withExtHeaders(Map.of("Content-Type", contentType))
        );

        return new SignedUploadUrl(
                objectKey,
                signedUrl.toString(),
                Instant.now().plus(duration)
        );
    }

    @Override
    public Optional<StoredObject> getObject(String objectKey) {
        Blob blob = storage.get(BlobId.of(storageProperties.gcs().bucket(), objectKey));
        if (blob == null)
            return Optional.empty();

        return Optional.of(new StoredObject(
                objectKey,
                blob.getContentType(),
                blob.getSize()
        ));
    }

    @Override
    public String getPublicUrl(String objectKey) {
        String baseUrl = storageProperties.gcs().publicBaseUrl();
        String encodedPath = UriUtils.encodePath(objectKey, StandardCharsets.UTF_8);
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/")
                .path(encodedPath)
                .build(true)
                .toUriString();
    }

    @Override
    public boolean exists(String objectKey) {
        return storage.get(BlobId.of(storageProperties.gcs().bucket(), objectKey)) != null;
    }

    @Override
    public void delete(String objectKey) {
        storage.delete(BlobId.of(storageProperties.gcs().bucket(), objectKey));
    }
}

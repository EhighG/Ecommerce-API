package com.ecommerce.api.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String provider,
        Gcs gcs
) {
    public record Gcs(
            String projectId,
            String bucket,
            String uploadPrefix,
            String publicBaseUrl
    ) {}
}

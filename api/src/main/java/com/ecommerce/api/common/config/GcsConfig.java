package com.ecommerce.api.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class GcsConfig {

    private final StorageProperties properties;

    @Bean
    public GoogleCredentials googleCredentials() throws IOException {
        return GoogleCredentials.getApplicationDefault()
                .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
    }

    @Bean
    public Storage gcsStorage() {
        return StorageOptions.newBuilder()
                .setProjectId(properties.gcs().projectId())
                .build()
                .getService();
    }
}

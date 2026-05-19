package com.ecommerce.api.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.loadtest.auth")
public record LoadTestAuthProperties(
        boolean enabled,
        String secret
) {}

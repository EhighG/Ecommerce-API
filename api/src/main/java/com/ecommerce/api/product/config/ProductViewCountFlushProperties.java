package com.ecommerce.api.product.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.product-view-count.flush")
public record ProductViewCountFlushProperties(
        @Min(1) int batchSize,
        @Min(1) int maxBatchesPerRun
) {
}

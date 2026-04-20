package com.ecommerce.api.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ImageIdWithOrder(
        @NotNull Long imageId,
        @NotNull @Min(1) Integer order
) {}

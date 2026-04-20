package com.ecommerce.api.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ModifyInventoryReq(
        @NotNull Long productId,
        @NotNull @Min(0) Integer quantity
) {
}

package com.ecommerce.api.cartitem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddItemReq(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity
) {
}

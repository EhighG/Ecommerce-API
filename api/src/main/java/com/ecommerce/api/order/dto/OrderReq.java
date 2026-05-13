package com.ecommerce.api.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record OrderReq(
        @NotEmpty List<@NotNull @Valid OrderItemReq> items
) {
    public record OrderItemReq(
            @NotNull Long cartItemId,
            @NotNull @Positive Integer orderQuantity,
            Long couponIssuedId
    ) {}
}

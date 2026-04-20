package com.ecommerce.api.cartitem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * '장바구니에 담기' 동작을 위해 cartItemId가 아닌 productId를 받음
 */
public record ChangeQuantityReq(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity
) {
}

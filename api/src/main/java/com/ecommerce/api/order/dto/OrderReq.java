package com.ecommerce.api.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record OrderReq(
        @NotEmpty(message = "주문 항목은 1개 이상이어야 합니다.") List<@NotNull(message = "주문 항목은 필수입니다.") @Valid OrderItemReq> items
) {
    public record OrderItemReq(
            @NotNull(message = "장바구니 항목 ID는 필수입니다.") Long cartItemId,
            @NotNull(message = "주문 수량은 필수입니다.") @Positive(message = "주문 수량은 1개 이상이어야 합니다.") Integer orderQuantity,
            Long couponIssuedId
    ) {}
}

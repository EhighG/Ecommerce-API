package com.ecommerce.api.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ModifyInventoryReq(
        @NotNull(message = "상품 ID는 필수입니다.") Long productId,
        @NotNull(message = "재고 수량은 필수입니다.") @Min(value = 0, message = "재고 수량은 0개 이상이어야 합니다.") Integer quantity
) {
}

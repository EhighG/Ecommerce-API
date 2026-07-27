package com.ecommerce.api.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ImageIdWithOrder(
        @NotNull(message = "이미지 ID는 필수입니다.") Long imageId,
        @NotNull(message = "이미지 표시 순서는 필수입니다.") @Min(value = 1, message = "이미지 표시 순서는 1 이상이어야 합니다.") Integer order
) {}

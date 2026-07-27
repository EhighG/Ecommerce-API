package com.ecommerce.api.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public record RegisterProductReq(
        @NotBlank(message = "상품명은 필수입니다.") String name,
        @NotNull(message = "상품 카테고리는 필수입니다.") Long categoryId,
        @NotBlank(message = "상품 설명은 필수입니다.") @Size(max = 1000, message = "상품 설명은 1000자 이하여야 합니다.") String description,
        @NotNull(message = "상품 가격은 필수입니다.") @Min(value = 0, message = "상품 가격은 0원 이상이어야 합니다.") Long unitPrice,
        @NotNull(message = "초기 재고 수량은 필수입니다.") @Min(value = 0, message = "초기 재고 수량은 0개 이상이어야 합니다.") Integer initialInventory,
        List<@Valid ImageIdWithOrder> imageIdList
) {
    public RegisterProductReq {
        if (imageIdList == null) imageIdList = new ArrayList<>();
    }
}

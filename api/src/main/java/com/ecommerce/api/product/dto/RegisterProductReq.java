package com.ecommerce.api.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public record RegisterProductReq(
        @NotBlank String name,
        @NotNull Long categoryId,
        @NotBlank @Size(min = 1, max = 1000) String description,
        @NotNull @Min(0) Long unitPrice,
        @NotNull @Min(0) Integer initialInventory,
        List<@Valid ImageIdWithOrder> imageIdList
) {
    public RegisterProductReq {
        if (imageIdList == null) imageIdList = new ArrayList<>();
    }
}

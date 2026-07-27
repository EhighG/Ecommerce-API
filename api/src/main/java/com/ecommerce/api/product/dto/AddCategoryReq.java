package com.ecommerce.api.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCategoryReq(
        @NotBlank(message = "카테고리명은 필수입니다.") @Size(max = 15, message = "카테고리명은 15자 이하여야 합니다.") String name
) {}

package com.ecommerce.api.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCategoryReq(
        @NotBlank @Size(max = 15) String name
) {}

package com.ecommerce.api.product.dto;

import com.ecommerce.api.product.entity.ProductCategory;

public record CategorySummary(
        Long id,
        String name
) {
    public CategorySummary(ProductCategory category) {
        this(category.getId(), category.getName());
    }
}

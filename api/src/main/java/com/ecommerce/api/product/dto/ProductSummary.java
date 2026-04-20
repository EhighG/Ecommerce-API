package com.ecommerce.api.product.dto;

import com.ecommerce.api.product.entity.Product;

public record ProductSummary(
        Long productId,
        String name,
        String thumbnailImageUrl
) {
    public ProductSummary(Product product, String thumbnailImageUrl) {
        this(product.getId(), product.getName(), thumbnailImageUrl);
    }
}
package com.ecommerce.api.product.dto;

public record ProductDetailDto(
        Long id,
        String name,
        Long categoryId,
        String categoryName,
        long unitPrice,
        String description,
        Long sellerId,
        String sellerNickname,
        int inventoryQuantity,
        double avgRating

) {
}

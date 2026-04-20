package com.ecommerce.api.product.dto;

public record ProductListDto(
        Long id,
        String name,
        String categoryName,
        String thumbnailObjectKey,
        long unitPrice,
        Long sellerId,
        String sellerNickname,
        int inventoryQuantity,
        double avgHalfStars
) {
    public double avgRating() {
        return avgHalfStars / 2.0;
    }
}

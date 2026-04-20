package com.ecommerce.api.product.dto;

import com.ecommerce.api.user.dto.UserSummary;

public record ProductListRes(
        Long id,
        String name,
        String categoryName,
        long unitPrice,
        String thumbnailImageUrl,
        UserSummary seller,
        int inventoryQuantity,
        // halfStars 아닌 실제 평점(0~5)
        double avgRating
) {
    public static ProductListRes forProductList(ProductListDto product, String thumbnailImageUrl) {
        return new ProductListRes(
                product.id(),
                product.name(),
                product.categoryName(),
                product.unitPrice(),
                thumbnailImageUrl,
                new UserSummary(product.sellerId(), product.sellerNickname()),
                product.inventoryQuantity(),
                product.avgRating()
        );
    }

    public static ProductListRes forCart(ProductListDto product, String thumbnailImageUrl) {
        return new ProductListRes(
                product.id(),
                product.name(),
                product.categoryName(),
                product.unitPrice(),
                thumbnailImageUrl,
                new UserSummary(product.sellerId(), product.sellerNickname()),
                product.inventoryQuantity(),
                product.avgRating()
        );
    }
}

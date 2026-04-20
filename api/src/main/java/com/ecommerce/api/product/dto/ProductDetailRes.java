package com.ecommerce.api.product.dto;

import com.ecommerce.api.user.dto.UserSummary;

import java.util.Collections;
import java.util.List;

public record ProductDetailRes(
        Long id,
        String name,
        CategorySummary category,
        long unitPrice,
        String description,
        List<String> imageUrls,
        UserSummary seller,
        int inventoryQuantity,
        double avgRating
) {
    public static ProductDetailRes of(ProductDetailDto product, List<String> imageUrls) {
        if (imageUrls == null)
            imageUrls = Collections.emptyList();


        return new ProductDetailRes(
                product.id(),
                product.name(),
                new CategorySummary(product.categoryId(), product.categoryName()),
                product.unitPrice(),
                product.description(),
                imageUrls,
                new UserSummary(product.sellerId(), product.sellerNickname()),
                product.inventoryQuantity(),
                product.avgRating()
        );
    }
}

package com.ecommerce.api.order.dto;

import com.ecommerce.api.product.dto.ProductListRes;

import java.util.List;

public record OrderItemSearchRes(
        List<ProductListRes> items,
        int page,
        int size,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
}

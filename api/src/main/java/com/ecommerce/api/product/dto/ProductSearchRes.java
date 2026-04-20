package com.ecommerce.api.product.dto;

import java.util.List;

public record ProductSearchRes(
        List<ProductListRes> items,
        int page,
        int size,
        long totalCount,
        int totalPages,
        boolean hasNext
) {}

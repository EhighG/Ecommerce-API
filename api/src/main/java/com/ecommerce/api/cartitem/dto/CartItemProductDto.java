package com.ecommerce.api.cartitem.dto;

import com.ecommerce.api.product.dto.ProductListDto;

public record CartItemProductDto(
        Long cartItemId,
        int quantity,
        long linePrice,
        ProductListDto productListDto
) {}

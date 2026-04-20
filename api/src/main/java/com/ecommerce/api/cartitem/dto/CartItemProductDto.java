package com.ecommerce.api.cartitem.dto;

import com.ecommerce.api.cartitem.entity.CartItem;
import com.ecommerce.api.product.dto.ProductListDto;

public record CartItemProductDto(
        CartItem cartItem,
        ProductListDto productListDto
) {}

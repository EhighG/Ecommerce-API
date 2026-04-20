package com.ecommerce.api.cartitem.dto;

import com.ecommerce.api.cartitem.entity.CartItem;
import com.ecommerce.api.product.dto.ProductListRes;

public record CartItemListRes(
        Long id,
        ProductListRes product,
        int quantity,
        long linePrice
) {
    public static CartItemListRes from(CartItem cartItem, ProductListRes productListRes) {
        return new CartItemListRes(
                cartItem.getId(),
                productListRes,
                cartItem.getQuantity(),
                cartItem.getLinePrice()
        );
    }
}

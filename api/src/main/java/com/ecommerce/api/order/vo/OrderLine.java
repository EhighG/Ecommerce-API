package com.ecommerce.api.order.vo;

public record OrderLine(
        ProductSnapshot product,
        int quantity
) {
    public OrderLine {
        if (product == null)
            throw new IllegalStateException("product is null");
        if (product.getSeller() == null)
            throw new IllegalStateException("seller is null");
        if (quantity <= 0)
            throw new IllegalStateException("quantity must be same or greater than 0");
    }

    public long linePrice() {
        return product.getUnitPrice() * quantity;
    }
}
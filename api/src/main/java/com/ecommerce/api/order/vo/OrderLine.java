package com.ecommerce.api.order.vo;

public record OrderLine(
        ProductSnapshot product,
        int quantity,
        Long couponIssuedId,
        long discountAmount
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

    public long finalLinePrice() {
        return linePrice() - discountAmount;
    }

    public boolean hasCoupon() {
        return couponIssuedId != null;
    }
}
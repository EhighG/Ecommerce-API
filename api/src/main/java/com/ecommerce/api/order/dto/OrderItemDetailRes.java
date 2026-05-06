package com.ecommerce.api.order.dto;

import com.ecommerce.api.coupon.entity.OrderItemCoupon;
import com.ecommerce.api.order.entity.OrderItem;

public record OrderItemDetailRes(
        OrderItemListRes orderItemListRes
) {
    public OrderItemDetailRes(OrderItem orderItem, String productThumbnail) {
        this(new OrderItemListRes(orderItem, productThumbnail));
    }

    public OrderItemDetailRes(OrderItem orderItem, String productThumbnail, OrderItemCoupon orderItemCoupon) {
        this(new OrderItemListRes(orderItem, productThumbnail, orderItemCoupon));
    }
}

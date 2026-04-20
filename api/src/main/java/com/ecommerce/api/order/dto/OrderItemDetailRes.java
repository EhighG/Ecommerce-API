package com.ecommerce.api.order.dto;

import com.ecommerce.api.order.entity.OrderItem;

public record OrderItemDetailRes(
        OrderItemListRes orderItemListRes
) {
    public OrderItemDetailRes(OrderItem orderItem, String productThumbnail) {
        this(new OrderItemListRes(orderItem, productThumbnail));
    }
}

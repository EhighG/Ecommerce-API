package com.ecommerce.api.order.dto;

import com.ecommerce.api.order.entity.Order;

import java.time.Instant;

public record OrderListRes(
        Long orderId,
        long totalPrice,
        Instant orderedAt,
        int itemCount
) {
    public OrderListRes(Order order) {
        this(
                order.getId(),
                order.getTotalPrice(),
                order.getOrderedAt(),
                order.getItemList().size()
        );
    }
}

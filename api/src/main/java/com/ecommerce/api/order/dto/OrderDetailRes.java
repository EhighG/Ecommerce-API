package com.ecommerce.api.order.dto;

import com.ecommerce.api.order.entity.Order;

import java.time.Instant;
import java.util.List;

public record OrderDetailRes(
        Long orderId,
        long totalPrice,
        Instant orderedAt,
        List<OrderItemListRes> itemList
) {
    public OrderDetailRes(Order order, List<OrderItemListRes> itemList) {
        this(
                order.getId(),
                order.getTotalPrice(),
                order.getOrderedAt(),
                itemList
        );
    }
}

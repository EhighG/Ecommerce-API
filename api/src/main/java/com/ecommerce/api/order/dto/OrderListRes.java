package com.ecommerce.api.order.dto;

import java.time.Instant;

public record OrderListRes(
        Long orderId,
        long totalPrice,
        Instant orderedAt,
        int itemCount
) {
    public OrderListRes(OrderListDto orderListDto) {
        this(
                orderListDto.orderId(),
                orderListDto.totalPrice(),
                orderListDto.orderedAt(),
                Math.toIntExact(orderListDto.itemCount())
        );
    }
}

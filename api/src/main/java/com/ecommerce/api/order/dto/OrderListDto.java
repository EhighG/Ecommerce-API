package com.ecommerce.api.order.dto;

import java.time.Instant;

public record OrderListDto(
        Long orderId,
        long totalPrice,
        long itemCount,
        Instant orderedAt
) {
}

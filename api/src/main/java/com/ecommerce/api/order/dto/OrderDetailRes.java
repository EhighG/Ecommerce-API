package com.ecommerce.api.order.dto;

import com.ecommerce.api.order.entity.Order;
import com.ecommerce.api.order.enums.OrderStatus;

import java.time.Instant;
import java.util.List;

public record OrderDetailRes(
        Long orderId,
        long totalPrice,
        long currentTotalPrice,
        Instant orderedAt,
        List<OrderItemListRes> itemList
) {
    public OrderDetailRes(Order order, List<OrderItemListRes> itemList) {
        this(
                order.getId(),
                order.getTotalPrice(),
                calculateCurrentTotalPrice(itemList),
                order.getOrderedAt(),
                itemList
        );
    }

    // totalPrice는 주문 시점 값이다. 취소를 반영한 금액은 저장하지 않고 조회할 때 계산한다
    private static long calculateCurrentTotalPrice(List<OrderItemListRes> itemList) {
        return itemList.stream()
                .filter(item -> item.status() != OrderStatus.CANCELED)
                .mapToLong(OrderItemListRes::finalLinePrice)
                .sum();
    }
}

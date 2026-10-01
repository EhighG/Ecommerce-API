package com.ecommerce.api.order.dto;

import com.ecommerce.api.order.entity.Order;
import com.ecommerce.api.order.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.ecommerce.api.order.enums.OrderStatus.CANCELED;
import static com.ecommerce.api.order.enums.OrderStatus.DELIVERED;
import static com.ecommerce.api.order.enums.OrderStatus.ORDERED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderDetailResTest {

    @Test
    @DisplayName("취소된 항목이 없으면 현재 금액은 주문 총액과 같다")
    void currentTotalPrice_withoutCanceledItem_equalsTotalPrice() {
        OrderDetailRes res = new OrderDetailRes(order(15_000L), List.of(
                item(1L, 10_000L, 9_000L, ORDERED),
                item(2L, 6_000L, 6_000L, DELIVERED)
        ));

        assertThat(res.totalPrice()).isEqualTo(15_000L);
        assertThat(res.currentTotalPrice()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("일부 항목이 취소되면 현재 금액은 취소되지 않은 항목의 최종 금액 합이고 주문 총액은 그대로다")
    void currentTotalPrice_withPartlyCanceledItems_excludesCanceled() {
        OrderDetailRes res = new OrderDetailRes(order(15_000L), List.of(
                item(1L, 10_000L, 9_000L, CANCELED),
                item(2L, 6_000L, 6_000L, ORDERED)
        ));

        assertThat(res.totalPrice()).isEqualTo(15_000L);
        assertThat(res.currentTotalPrice()).isEqualTo(6_000L);
    }

    @Test
    @DisplayName("모든 항목이 취소되면 현재 금액은 0이다")
    void currentTotalPrice_withAllCanceledItems_isZero() {
        OrderDetailRes res = new OrderDetailRes(order(15_000L), List.of(
                item(1L, 10_000L, 9_000L, CANCELED),
                item(2L, 6_000L, 6_000L, CANCELED)
        ));

        assertThat(res.currentTotalPrice()).isZero();
    }

    private Order order(long totalPrice) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(1L);
        when(order.getTotalPrice()).thenReturn(totalPrice);
        return order;
    }

    private OrderItemListRes item(Long id, long linePrice, long finalLinePrice, OrderStatus status) {
        return new OrderItemListRes(id, null, 1, linePrice, null, finalLinePrice, status, null);
    }
}

package com.ecommerce.api.coupon.service;

import com.ecommerce.api.coupon.entity.CouponIssued;
import com.ecommerce.api.coupon.entity.OrderItemCoupon;
import com.ecommerce.api.coupon.repository.OrderItemCouponRepository;
import com.ecommerce.api.order.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class OrderItemCouponService {

    private final OrderItemCouponRepository orderItemCouponRepository;

    @Transactional
    public Long create(OrderItem orderItem, CouponIssued couponIssued, long discountedAmount) {
        return orderItemCouponRepository.save(
                new OrderItemCoupon(orderItem, couponIssued, discountedAmount, Instant.now())
        ).getId();
    }

    public OrderItemCoupon findByOrderItemId(Long orderItemId) {
        return orderItemCouponRepository.findByOrderItemId(orderItemId).orElse(null);
    }

    public Map<Long, OrderItemCoupon> findByOrderItemId(List<OrderItem> orderItems) {
        List<Long> orderItemIds = orderItems.stream()
                .map(OrderItem::getId)
                .toList();

        if (orderItemIds.isEmpty()) {
            return Map.of();
        }

        return orderItemCouponRepository.findAllByOrderItemIdIn(orderItemIds).stream()
                .collect(Collectors.toMap(
                        orderItemCoupon -> orderItemCoupon.getOrderItem().getId(),
                        Function.identity()
                ));
    }
}

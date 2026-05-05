package com.ecommerce.api.coupon.repository;

import com.ecommerce.api.coupon.entity.OrderItemCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderItemCouponRepository extends JpaRepository<OrderItemCoupon, Long> {
    Optional<OrderItemCoupon> findByOrderItemId(Long orderItemId);
}

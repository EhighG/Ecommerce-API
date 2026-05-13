package com.ecommerce.api.coupon.entity;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.util.BaseTimeEntity;
import com.ecommerce.api.order.entity.OrderItem;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static com.ecommerce.api.common.exception.ErrorCode.INVALID_INPUT;

/**
 * 주문 시 OrderItem에 사용된 쿠폰 스냅샷
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "order_item_coupon",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_order_item_coupon_order_item", columnNames = "order_item_id"),
        }
)
public class OrderItemCoupon extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Embedded
    private UsedCouponSnapshot usedCoupon;

    public OrderItemCoupon(OrderItem orderItem, CouponIssued couponIssued, long discountedAmount, Instant usedAt) {
        if (orderItem == null || couponIssued == null || usedAt == null) {
            throw new AppException(INVALID_INPUT, "Invalid order item coupon");
        }
        if (discountedAmount <= 0) {
            throw new AppException(INVALID_INPUT, "Discount amount must be positive");
        }

        this.orderItem = orderItem;
        this.usedCoupon = new UsedCouponSnapshot(couponIssued, discountedAmount, usedAt);
    }
}

package com.ecommerce.api.coupon.entity;

import com.ecommerce.api.coupon.enums.CouponType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class UsedCouponSnapshot {

    @Column(name = "coupon_issued_id", nullable = false)
    private Long couponIssuedId;

    @Column(name = "coupon_event_id", nullable = false)
    private Long couponEventId;

    @Column(name = "coupon_name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false)
    private CouponType type;

    @Column(name = "coupon_discount_value", nullable = false)
    private long discountValue;

    @Column(name = "coupon_max_discount_amount", nullable = false)
    private long maxDiscountAmount;

    @Column(nullable = false)
    private long discountedAmount;

    @Column(nullable = false)
    private Instant usedAt;

    public UsedCouponSnapshot(CouponIssued couponIssued, long discountedAmount, Instant usedAt) {
        CouponEvent couponEvent = couponIssued.getCouponEvent();

        this.couponIssuedId = couponIssued.getId();
        this.couponEventId = couponEvent.getId();
        this.name = couponEvent.getName();
        this.type = couponEvent.getType();
        this.discountValue = couponEvent.getDiscountValue();
        this.maxDiscountAmount = couponEvent.getMaxDiscountAmount();
        this.discountedAmount = discountedAmount;
        this.usedAt = usedAt;
    }
}

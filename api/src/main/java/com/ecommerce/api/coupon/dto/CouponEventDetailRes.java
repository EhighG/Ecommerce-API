package com.ecommerce.api.coupon.dto;

import com.ecommerce.api.coupon.entity.CouponEvent;
import com.ecommerce.api.coupon.enums.CouponType;

import java.time.Instant;

public record CouponEventDetailRes(
        Long couponEventId,
        String name,
        CouponType type,
        long discountValue,
        long maxDiscountAmount,
        int initialQuantity,
        Integer remainingQuantity,
        Instant startAt,
        Instant endAt,
        long validSeconds,
        boolean open
) {
    public CouponEventDetailRes(CouponEventCache couponEvent, Integer remainingQuantity, Instant now) {
        this(
                couponEvent.couponEventId(),
                couponEvent.name(),
                couponEvent.type(),
                couponEvent.discountValue(),
                couponEvent.maxDiscountAmount(),
                couponEvent.initialQuantity(),
                remainingQuantity,
                couponEvent.startAt(),
                couponEvent.endAt(),
                couponEvent.validSeconds(),
                couponEvent.isOpen(now)
        );
    }

    public CouponEventDetailRes(CouponEvent couponEvent, Instant now) {
        this(
                couponEvent.getId(),
                couponEvent.getName(),
                couponEvent.getType(),
                couponEvent.getDiscountValue(),
                couponEvent.getMaxDiscountAmount(),
                couponEvent.getInitialQuantity(),
                null,
                couponEvent.getStartAt(),
                couponEvent.getEndAt(),
                couponEvent.getValidSeconds(),
                couponEvent.isOpen(now)
        );
    }
}

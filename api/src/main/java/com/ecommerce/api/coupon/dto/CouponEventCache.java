package com.ecommerce.api.coupon.dto;

import com.ecommerce.api.coupon.entity.CouponEvent;
import com.ecommerce.api.coupon.enums.CouponType;

import java.time.Instant;

public record CouponEventCache(
        Long couponEventId,
        String name,
        CouponType type,
        long discountValue,
        long maxDiscountAmount,
        int initialQuantity,
        boolean active,
        Instant startAt,
        Instant endAt,
        long validSeconds
) {
    public static CouponEventCache from(CouponEvent couponEvent) {
        return new CouponEventCache(
                couponEvent.getId(),
                couponEvent.getName(),
                couponEvent.getType(),
                couponEvent.getDiscountValue(),
                couponEvent.getMaxDiscountAmount(),
                couponEvent.getInitialQuantity(),
                couponEvent.isActive(),
                couponEvent.getStartAt(),
                couponEvent.getEndAt(),
                couponEvent.getValidSeconds()
        );
    }

    public boolean isOpen(Instant now) {
        return active && !now.isBefore(startAt) && now.isBefore(endAt);
    }
}

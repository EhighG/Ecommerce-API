package com.ecommerce.api.coupon.dto;

import com.ecommerce.api.coupon.entity.CouponIssued;

import java.time.Instant;

public record IssueCouponRes(
        Long couponIssuedId,
        Long couponEventId,
        Instant issuedAt,
        Instant expiresAt
) {
    public IssueCouponRes(CouponIssued couponIssued) {
        this(
                couponIssued.getId(),
                couponIssued.getCouponEvent().getId(),
                couponIssued.getIssuedAt(),
                couponIssued.getExpiresAt()
        );
    }
}

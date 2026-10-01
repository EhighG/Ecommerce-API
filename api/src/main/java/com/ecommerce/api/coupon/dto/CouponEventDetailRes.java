package com.ecommerce.api.coupon.dto;

import com.ecommerce.api.coupon.entity.CouponEvent;
import com.ecommerce.api.coupon.enums.CouponType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record CouponEventDetailRes(
        Long couponEventId,
        String name,
        CouponType type,
        long discountValue,
        long maxDiscountAmount,
        int initialQuantity,
        @Schema(description = "남은 수량. 이벤트가 Redis에 올라가 있지 않으면 `null`이다. 발급 시작 10분 전보다 이를 때, 종료 10분 뒤부터, "
                + "Redis를 잃고 다시 적재하기 전이 그렇다. 적재는 최대 30초 늦을 수 있다.") Integer remainingQuantity,
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

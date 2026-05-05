package com.ecommerce.api.coupon.dto;

import com.ecommerce.api.coupon.enums.CouponType;
import com.ecommerce.api.coupon.enums.CouponValidDurationUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateCouponEventReq(
        @NotBlank String name,
        @NotNull CouponType type,
        @Positive long discountValue,
        @Positive long maxDiscountAmount,
        @Positive int initialQuantity,
        @NotBlank String startAt,
        @NotBlank String endAt,
        String timezone,
        @Positive long validDurationAmount,
        @NotNull CouponValidDurationUnit durationUnit
) {
    public long validSeconds() {
        return durationUnit.toSeconds(validDurationAmount);
    }
}

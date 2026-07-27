package com.ecommerce.api.coupon.dto;

import com.ecommerce.api.coupon.enums.CouponType;
import com.ecommerce.api.coupon.enums.CouponValidDurationUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateCouponEventReq(
        @NotBlank(message = "쿠폰 이벤트 이름은 필수입니다.") String name,
        @NotNull(message = "쿠폰 종류는 필수입니다.") CouponType type,
        @Positive(message = "쿠폰 할인값은 1 이상이어야 합니다.") long discountValue,
        @Positive(message = "최대 할인 금액은 1원 이상이어야 합니다.") long maxDiscountAmount,
        @Positive(message = "발급 가능 수량은 1개 이상이어야 합니다.") int initialQuantity,
        @NotBlank(message = "쿠폰 이벤트 시작 일시는 필수입니다.") String startAt,
        @NotBlank(message = "쿠폰 이벤트 종료 일시는 필수입니다.") String endAt,
        String timezone,
        @Positive(message = "쿠폰 유효 기간은 1 이상이어야 합니다.") long validDurationAmount,
        @NotNull(message = "쿠폰 유효 기간 단위는 필수입니다.") CouponValidDurationUnit durationUnit
) {
    public long validSeconds() {
        return durationUnit.toSeconds(validDurationAmount);
    }
}

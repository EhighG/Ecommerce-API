package com.ecommerce.api.coupon.dto;

import com.ecommerce.api.coupon.enums.CouponType;
import com.ecommerce.api.coupon.enums.CouponValidDurationUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateCouponEventReq(
        @NotBlank(message = "쿠폰 이벤트 이름은 필수입니다.") String name,
        @NotNull(message = "쿠폰 종류는 필수입니다.") CouponType type,
        @Schema(description = "`FIXED_AMOUNT`이면 할인 금액(원), `PERCENT`이면 할인율(%)") @Positive(message = "쿠폰 할인값은 1 이상이어야 합니다.") long discountValue,
        @Schema(description = "할인액 상한(원)") @Positive(message = "최대 할인 금액은 1원 이상이어야 합니다.") long maxDiscountAmount,
        @Positive(message = "발급 가능 수량은 1개 이상이어야 합니다.") int initialQuantity,
        @Schema(description = "발급 시작 시각. `yyyy-MM-dd HH:mm:ss`, `timezone` 기준", example = "2026-10-01 10:00:00") @NotBlank(message = "쿠폰 이벤트 시작 일시는 필수입니다.") String startAt,
        @Schema(description = "발급 종료 시각. `yyyy-MM-dd HH:mm:ss`, `timezone` 기준", example = "2026-10-08 23:59:59") @NotBlank(message = "쿠폰 이벤트 종료 일시는 필수입니다.") String endAt,
        @Schema(description = "IANA 시간대 ID. 비우면 `Asia/Seoul`", example = "Asia/Seoul") String timezone,
        @Schema(description = "발급 시점부터 쿠폰을 쓸 수 있는 기간. 단위는 `durationUnit`") @Positive(message = "쿠폰 유효 기간은 1 이상이어야 합니다.") long validDurationAmount,
        @NotNull(message = "쿠폰 유효 기간 단위는 필수입니다.") CouponValidDurationUnit durationUnit
) {
    public long validSeconds() {
        return durationUnit.toSeconds(validDurationAmount);
    }
}

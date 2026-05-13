package com.ecommerce.api.coupon.enums;

import java.util.concurrent.TimeUnit;

public enum CouponValidDurationUnit {
    MINUTES,
    HOURS,
    DAYS;

    public long toSeconds(long amount) {
        return switch (this) {
            case MINUTES -> TimeUnit.MINUTES.toSeconds(amount);
            case HOURS -> TimeUnit.HOURS.toSeconds(amount);
            case DAYS -> TimeUnit.DAYS.toSeconds(amount);
        };
    }
}

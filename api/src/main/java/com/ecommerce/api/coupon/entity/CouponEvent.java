package com.ecommerce.api.coupon.entity;


import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.util.BaseTimeEntity;
import com.ecommerce.api.coupon.enums.CouponType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static com.ecommerce.api.common.exception.ErrorCode.INVALID_INPUT;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "coupon_event")
public class CouponEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // eventName
    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private long discountValue;

    @Column(nullable = false)
    private long maxDiscountAmount;

    @Column(nullable = false)
    private int initialQuantity;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    @Column(nullable = false)
    private long validSeconds;

    @Column(nullable = false)
    private boolean active = true;

    public CouponEvent(String name, CouponType type, long discountValue,
                       long maxDiscountAmount, int initialQuantity,
                       Instant startAt, Instant endAt, long validSeconds) {
        validate(name, type, discountValue, maxDiscountAmount, initialQuantity, startAt, endAt, validSeconds);

        this.name = name;
        this.type = type;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.initialQuantity = initialQuantity;
        this.startAt = startAt;
        this.endAt = endAt;
        this.validSeconds = validSeconds;
    }

    private void validate(
            String name,
            CouponType type,
            long discountValue,
            long maxDiscountAmount,
            int initialQuantity,
            Instant startAt,
            Instant endAt,
            long validSeconds
    ) {
        if (name == null || name.isBlank()) {
            throw new AppException(INVALID_INPUT, "쿠폰 이벤트 이름은 필수입니다.");
        }
        if (type == null) {
            throw new AppException(INVALID_INPUT, "쿠폰 종류는 필수입니다.");
        }
        if (discountValue <= 0) {
            throw new AppException(INVALID_INPUT, "쿠폰 할인값은 1 이상이어야 합니다.");
        }
        if (CouponType.PERCENT.equals(type) && discountValue > 100) {
            throw new AppException(INVALID_INPUT, "정률 쿠폰 할인율은 1 이상 100 이하이어야 합니다.");
        }
        if (maxDiscountAmount <= 0) {
            throw new AppException(INVALID_INPUT, "최대 할인 금액은 1원 이상이어야 합니다.");
        }
        if (initialQuantity <= 0) {
            throw new AppException(INVALID_INPUT, "발급 가능 수량은 1개 이상이어야 합니다.");
        }
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new AppException(INVALID_INPUT, "쿠폰 이벤트 기간이 올바르지 않습니다.");
        }
        if (validSeconds <= 0) {
            throw new AppException(INVALID_INPUT, "쿠폰 유효 기간은 1초 이상이어야 합니다.");
        }
    }

    public Instant calculateExpiresAt(Instant issuedAt) {
        return issuedAt.plusSeconds(validSeconds);
    }

    public long calculateDiscountAmount(long amount) {
        if (amount <= 0) {
            throw new AppException(INVALID_INPUT);
        }

        long discountAmount = switch (type) {
            case FIXED_AMOUNT -> discountValue;
            case PERCENT -> amount * discountValue / 100;
        };

        return Math.min(
                Math.min(discountAmount, maxDiscountAmount),
                amount
        );
    }

    public boolean isOpen(Instant now) {
        return active && !now.isBefore(startAt) && now.isBefore(endAt);
    }
}

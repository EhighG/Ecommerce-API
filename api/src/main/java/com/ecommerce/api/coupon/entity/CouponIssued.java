package com.ecommerce.api.coupon.entity;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.common.util.BaseTimeEntity;
import com.ecommerce.api.coupon.enums.CouponStatus;
import com.ecommerce.api.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static com.ecommerce.api.common.exception.ErrorCode.*;
import static com.ecommerce.api.coupon.enums.CouponStatus.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "coupon_issued",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_coupon_issued_event_user",
                        columnNames = {"coupon_event_id", "user_id"}
                )
        }
)
public class CouponIssued extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_event_id", nullable = false)
    private CouponEvent couponEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status = ISSUED;

    @Column(nullable = false)
    private Instant issuedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    public CouponIssued(CouponEvent couponEvent, User user, Instant issuedAt) {
        if (couponEvent == null ||  user == null || issuedAt == null) {
            throw new AppException(INVALID_INPUT);
        }

        this.couponEvent = couponEvent;
        this.user = user;
        this.issuedAt = issuedAt;
        this.expiresAt = couponEvent.calculateExpiresAt(issuedAt);
    }

    public boolean isUsable(Instant now) {
        return status == ISSUED && now.isBefore(expiresAt);
    }

    public void use(Instant now) {
        if (!isUsable(now)) {
            throw new AppException(COUPON_EXPIRED);
        }

        this.status = USED;
        this.usedAt = now;
    }

    public void restore(Instant now) {
        if (status != USED) {
            throw new AppException(INVALID_COUPON_STATUS, "사용된 쿠폰이 아닙니다.");
        }

        if (!now.isBefore(expiresAt)) {
            this.status = EXPIRED;
            return;
        }

        this.status = ISSUED;
        this.usedAt = null;
    }

    public void expire() {
        if (status == USED)
            throw new AppException(INVALID_COUPON_STATUS, "이미 사용된 쿠폰입니다.");
        this.status = EXPIRED;
    }
}

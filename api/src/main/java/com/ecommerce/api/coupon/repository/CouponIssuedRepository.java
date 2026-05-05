package com.ecommerce.api.coupon.repository;

import com.ecommerce.api.coupon.entity.CouponIssued;
import com.ecommerce.api.coupon.enums.CouponStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CouponIssuedRepository extends JpaRepository<CouponIssued, Long> {
    boolean existsByCouponEventIdAndUserId(Long couponEventId, Long userId);

    Optional<CouponIssued> findByIdAndUserId(Long id, Long userId);

    List<CouponIssued> findAllByStatusAndExpiresAtBefore(CouponStatus status, Instant cutoff);
}

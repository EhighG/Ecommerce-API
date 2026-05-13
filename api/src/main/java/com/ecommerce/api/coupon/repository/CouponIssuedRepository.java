package com.ecommerce.api.coupon.repository;

import com.ecommerce.api.coupon.entity.CouponIssued;
import com.ecommerce.api.coupon.enums.CouponStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CouponIssuedRepository extends JpaRepository<CouponIssued, Long> {
    boolean existsByCouponEventIdAndUserId(Long couponEventId, Long userId);

    long countByCouponEventId(Long couponEventId);

    Optional<CouponIssued> findByIdAndUserId(Long id, Long userId);

    List<CouponIssued> findAllByStatusAndExpiresAtBefore(CouponStatus status, Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ci
            from CouponIssued ci
            join fetch ci.couponEvent
            where ci.id in :ids
            and ci.user.id = :userId
            """)
    List<CouponIssued> findAllByIdInAndUserIdForUpdate(List<Long> ids, Long userId);
}

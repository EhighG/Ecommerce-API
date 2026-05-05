package com.ecommerce.api.coupon.repository;

import com.ecommerce.api.coupon.entity.CouponEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface CouponEventRepository extends JpaRepository<CouponEvent, Long> {

    @Query("""
            select ce
            from CouponEvent ce
            where ce.active = true
            and ce.startAt <= :targetStartAt
            and ce.endAt > :now
            """)
    List<CouponEvent> findCacheTargets(Instant targetStartAt, Instant now);
}

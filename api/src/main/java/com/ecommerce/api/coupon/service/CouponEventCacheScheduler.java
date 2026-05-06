package com.ecommerce.api.coupon.service;

import com.ecommerce.api.coupon.entity.CouponEvent;
import com.ecommerce.api.coupon.repository.CouponEventRepository;
import com.ecommerce.api.coupon.repository.CouponIssuedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class CouponEventCacheScheduler {

    /**
     * 이벤트 시작시간부터는 캐시되어 있는 상태를 보장하기 위해, 미리 캐시해둠
     */
    private static final Duration CACHE_WARMUP_BEFORE_START = Duration.ofMinutes(10);

    /**
     * 이벤트 종료시간 후에도 캐시 저장소를 다시 조회할 수 있으므로(실패시 fallback 등),
     * TTL을 이벤트 종료시각보다 길게 설정
     */
    private static final Duration CACHE_TTL_BUFFER = Duration.ofMinutes(10);

    private final CouponEventRepository couponEventRepository;
    private final CouponIssuedRepository couponIssuedRepository;
    private final CouponEventCacheService couponEventCacheService;

    @Scheduled(fixedDelay = 30_000)
    public void cacheCouponEvents() {
        Instant now =  Instant.now();
        Instant targetStartAt = now.plus(CACHE_WARMUP_BEFORE_START);

        List<CouponEvent> couponEvents = couponEventRepository.findCacheTargets(targetStartAt, now);

        for (CouponEvent couponEvent : couponEvents) {
            cacheCouponEvent(couponEvent, now);
        }
    }

    private void cacheCouponEvent(CouponEvent couponEvent, Instant now) {
        Duration ttl = Duration.between(now, couponEvent.getEndAt().plus(CACHE_TTL_BUFFER));

        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }

        long issuedCount = couponIssuedRepository.countByCouponEventId(couponEvent.getId());
        int remainingQuantity = calculateRemainingQuantity(couponEvent, issuedCount);

        try {
            boolean initialized = couponEventCacheService.initCouponEventIfAbsent(
                    couponEvent, remainingQuantity, ttl
            );

            if (initialized) {
                log.info("CouponEvent 캐시됨. couponEventId = {}, remainingQuantity = {}",
                        couponEvent.getId(), remainingQuantity);
            }
        } catch (RuntimeException e) {
            log.error("CouponEvent 캐싱 실패. couponEventId = {}", couponEvent.getId(), e);
        }
    }

    private int calculateRemainingQuantity(CouponEvent couponEvent, long issuedCount) {
        long remainingQuantity = couponEvent.getInitialQuantity() - issuedCount;

        if (remainingQuantity <= 0) {
            return 0;
        }

        return Math.toIntExact(remainingQuantity);
    }
}

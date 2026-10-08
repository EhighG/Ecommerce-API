package com.ecommerce.api.coupon.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.coupon.dto.CouponEventCache;
import com.ecommerce.api.coupon.dto.CouponEventDetailRes;
import com.ecommerce.api.coupon.dto.CreateCouponEventReq;
import com.ecommerce.api.coupon.dto.IssueCouponRes;
import com.ecommerce.api.coupon.entity.CouponEvent;
import com.ecommerce.api.coupon.entity.CouponIssued;
import com.ecommerce.api.coupon.repository.CouponEventRepository;
import com.ecommerce.api.coupon.repository.CouponIssuedRepository;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import static com.ecommerce.api.common.exception.ErrorCode.*;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class CouponService {

    private static final Duration ISSUE_CACHE_TTL_BUFFER = Duration.ofMinutes(10);
    private static final ZoneId DEFAULT_COUPON_EVENT_TIMEZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter COUPON_EVENT_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CouponEventRepository couponEventRepository;
    private final CouponIssuedRepository couponIssuedRepository;
    private final UserRepository userRepository;
    private final CouponEventCacheService couponEventCacheService;

    public CouponEventDetailRes getCouponEventDetail(Long couponEventId) {
        Instant now = Instant.now();

        Optional<CouponEventCache> couponEventCache = couponEventCacheService.findCouponEventCache(couponEventId);
        if (couponEventCache.isPresent()) {
            int remainingQuantity = couponEventCacheService.getRemainingQuantity(couponEventId);
            return new CouponEventDetailRes(couponEventCache.get(), remainingQuantity, now);
        }
        // 없는경우 DB에서 조회
        CouponEvent couponEvent = getCouponEvent(couponEventId);
        return new CouponEventDetailRes(couponEvent, now);
    }

    @Transactional
    public IssueCouponRes issue(Long couponEventId, Long userId) {
        Instant now = Instant.now();

        CouponEventCache cache = couponEventCacheService.findCouponEventCache(couponEventId)
                .orElseThrow(() -> new AppException(COUPON_EVENT_CLOSED));

        if (!cache.isOpen(now)) {
            throw new AppException(COUPON_EVENT_CLOSED);
        }

        Duration ttl = calculateIssueCacheTtl(cache, now);

        CouponEventCacheService.IssueResult issueResult =
                couponEventCacheService.issue(couponEventId, userId, ttl);

        if (issueResult == CouponEventCacheService.IssueResult.ALREADY_ISSUED) {
            throw new AppException(COUPON_ALREADY_ISSUED);
        }
        if (issueResult == CouponEventCacheService.IssueResult.SOLD_OUT) {
            throw new AppException(COUPON_SOLD_OUT);
        }
        if (issueResult != CouponEventCacheService.IssueResult.SUCCESS) {
            throw new AppException(COUPON_ISSUE_FAILED);
        }

        try {
            CouponEvent couponEvent = getCouponEvent(couponEventId);
            User user = userRepository.findByIdAndDeletedFalse(userId)
                    .orElseThrow(() -> new AppException(USER_NOT_FOUND));

            CouponIssued saved = couponIssuedRepository.saveAndFlush(
                    new CouponIssued(couponEvent, user, now)
            );

            return new IssueCouponRes(saved);
        } catch (DataIntegrityViolationException e) {
            couponEventCacheService.compensateIssue(couponEventId, userId);
            throw new AppException(COUPON_ALREADY_ISSUED);
        } catch (RuntimeException e) {
            couponEventCacheService.compensateIssue(couponEventId, userId);
            throw e;
        }
    }

    @Transactional
    public List<CouponIssued> getCoupons(List<Long> couponIssuedIds, Long userId) {
        if (couponIssuedIds == null || couponIssuedIds.isEmpty()) {
            return List.of();
        }

        if (couponIssuedIds.stream().distinct().count() != couponIssuedIds.size()) {
            throw new AppException(DUPLICATED_COUPON);
        }

        List<CouponIssued> coupons = couponIssuedRepository
                .findAllByIdInAndUserIdForUpdate(couponIssuedIds, userId);

        if (coupons.size() != couponIssuedIds.size()) {
            throw new AppException(COUPON_ISSUED_NOT_FOUND);
        }

        return coupons;
    }

    private Duration calculateIssueCacheTtl(CouponEventCache cache, Instant now) {
        Duration ttl = Duration.between(now, cache.endAt().plus(ISSUE_CACHE_TTL_BUFFER));

        if (ttl.isZero() || ttl.isNegative()) {
            throw new AppException(COUPON_EVENT_CLOSED);
        }

        return ttl;
    }

    private CouponEvent getCouponEvent(Long couponEventId) {
        return couponEventRepository.findById(couponEventId)
                .orElseThrow(() -> new AppException(COUPON_EVENT_NOT_FOUND));
    }

    @Transactional
    public void restoreCouponIssued(Long couponIssuedId, Instant now) {
        CouponIssued couponIssued = couponIssuedRepository.findById(couponIssuedId)
                .orElseThrow(() -> new AppException(COUPON_ISSUED_NOT_FOUND));

        couponIssued.restore(now);
    }

    @Transactional
    public Long createCouponEvent(CreateCouponEventReq req) {
        Instant now = Instant.now();
        Instant startAt = toInstant(req.startAt(), req.timezone());
        Instant endAt = toInstant(req.endAt(), req.timezone());

        validateCouponEventPeriod(startAt, endAt, now);

        CouponEvent saved = couponEventRepository.save(
                new CouponEvent(
                        req.name(),
                        req.type(),
                        req.discountValue(),
                        req.maxDiscountAmount(),
                        req.initialQuantity(),
                        startAt,
                        endAt,
                        req.validSeconds()
                )
        );

        if (saved.isOpen(now)) {
            Duration ttl = calculateIssueCacheTtl(CouponEventCache.from(saved), now);
            couponEventCacheService.initCouponEventIfAbsent(saved, saved.getInitialQuantity(), ttl);
        }

        return saved.getId();
    }

    private void validateCouponEventPeriod(Instant startAt, Instant endAt, Instant now) {
        if (startAt.isBefore(endAt) && endAt.isAfter(now)) {
            return;
        }
        throw new AppException(INVALID_INPUT, "쿠폰 이벤트 기간이 올바르지 않습니다.");
    }

    private Instant toInstant(String dateTimeString, String timezone) {
        ZoneId zoneId = resolveZoneId(timezone);

        try {
            LocalDateTime dateTime = LocalDateTime.parse(dateTimeString, COUPON_EVENT_DATE_TIME_FORMATTER);
            return dateTime.atZone(zoneId).toInstant();
        } catch (DateTimeParseException e) {
            throw new AppException(INVALID_INPUT, "쿠폰 이벤트 일시 형식이 올바르지 않습니다.");
        }
    }

    private ZoneId resolveZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_COUPON_EVENT_TIMEZONE;
        }

        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new AppException(INVALID_INPUT, "시간대 형식이 올바르지 않습니다.");
        }
    }
}

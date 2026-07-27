package com.ecommerce.api.coupon.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.coupon.dto.CouponEventCache;
import com.ecommerce.api.coupon.entity.CouponEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.ecommerce.api.common.exception.ErrorCode.COUPON_EVENT_CACHING_FAILED;
import static com.ecommerce.api.common.exception.ErrorCode.COUPON_EVENT_READ_FAILED;

@RequiredArgsConstructor
@Service
public class CouponEventCacheService {

    private final String META_KEY = "coupon:event:%d:meta";
    private final String STOCK_KEY = "coupon:event:%d:stock";
    private final String USERS_KEY = "coupon:event:%d:users";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final RedisScript<Long> issueScript = RedisScript.of("""
            local stockKey = KEYS[1]
            local usersKey = KEYS[2]
            local userId = ARGV[1]
            local ttlSeconds = tonumber(ARGV[2])
            
            if redis.call('SISMEMBER', usersKey, userId) == 1 then
                return -1
            end
            
            local stock = tonumber(redis.call('GET', stockKey) or '0')
            if stock <= 0 then
                return -2
            end
            
            redis.call('DECR', stockKey)
            redis.call('SADD', usersKey, userId)
            redis.call('EXPIRE', usersKey, ttlSeconds)
            
            return 1
            """, Long.class);

    private final RedisScript<Long> compensateScript = RedisScript.of("""
            local stockKey = KEYS[1]
            local usersKey = KEYS[2]
            local userId = ARGV[1]
            
            if redis.call('SREM', usersKey, userId) == 1 then
                redis.call('INCR', stockKey)
                return 1
            end
            
            return 0
            """, Long.class);

    private final RedisScript<Long> initEventScript = RedisScript.of("""
            local metaKey = KEYS[1]
            local stockKey = KEYS[2]
            local usersKey = KEYS[3]
            
            local metaJson = ARGV[1]
            local remainingQuantity = ARGV[2]
            local ttlSeconds = tonumber(ARGV[3])
            
            if redis.call('EXISTS', metaKey) == 1 then
                return 0
            end
            
            redis.call('SET', metaKey, metaJson, 'EX', ttlSeconds)
            redis.call('SET', stockKey, remainingQuantity, 'EX', ttlSeconds)
            redis.call('DEL', usersKey)
            
            return 1
            """, Long.class);

    public Optional<CouponEventCache> findCouponEventCache(Long couponEventId) {
        String json = redisTemplate.opsForValue().get(metaKey(couponEventId));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(json, CouponEventCache.class));
        } catch (JacksonException e) {
            throw new AppException(COUPON_EVENT_READ_FAILED);
        }
    }

    public int getRemainingQuantity(Long couponEventId) {
        String stock = redisTemplate.opsForValue().get(stockKey(couponEventId));
        if (stock == null || stock.isBlank()) {
            throw new AppException(COUPON_EVENT_READ_FAILED, "쿠폰 잔여 수량을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
        }

        return Math.toIntExact(Long.parseLong(stock));
    }

    public IssueResult issue(Long couponEventId, Long userId, Duration ttl) {
        Long result = redisTemplate.execute(
                issueScript,
                List.of(stockKey(couponEventId), usersKey(couponEventId)),
                userId.toString(),
                String.valueOf(ttl.toSeconds())
        );

        if (Long.valueOf(1L).equals(result)) { // Wrapper타입 언박싱 과정에서의 NPE 방지
            return IssueResult.SUCCESS;
        }
        if (Long.valueOf(-1L).equals(result)) {
            return IssueResult.ALREADY_ISSUED;
        }
        if (Long.valueOf(-2L).equals(result)) {
            return IssueResult.SOLD_OUT;
        }

        return IssueResult.FAILED;
    }

    public void compensateIssue(Long couponEventId, Long userId) {
        redisTemplate.execute(
                compensateScript,
                List.of(stockKey(couponEventId), usersKey(couponEventId)),
                userId.toString()
        );
    }

    public boolean initCouponEventIfAbsent(CouponEvent couponEvent, int remainingQuantity, Duration ttl) {
        return initCouponEventIfAbsent(CouponEventCache.from(couponEvent), remainingQuantity, ttl);
    }

    public boolean initCouponEventIfAbsent(CouponEventCache cache, int remainingQuantity, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new AppException(COUPON_EVENT_CACHING_FAILED);
        }

        try {
            String json = objectMapper.writeValueAsString(cache);

            Long result = redisTemplate.execute(
                initEventScript,
                List.of(
                        metaKey(cache.couponEventId()),
                        stockKey(cache.couponEventId()),
                        usersKey(cache.couponEventId())
                ),
                json,
                String.valueOf(remainingQuantity),
                String.valueOf(ttl.toSeconds())
            );

            return Long.valueOf(1L).equals(result);
        } catch (JacksonException e) {
            throw new AppException(COUPON_EVENT_CACHING_FAILED, "쿠폰 이벤트 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    public void deleteEvent(Long couponEventId) {
        redisTemplate.delete(List.of(
                metaKey(couponEventId),
                stockKey(couponEventId),
                usersKey(couponEventId)
        ));
    }

    private String metaKey(Long couponEventId) {
        return META_KEY.formatted(couponEventId);
    }

    private String stockKey(Long couponEventId) {
        return STOCK_KEY.formatted(couponEventId);
    }

    private String usersKey(Long couponEventId) {
        return USERS_KEY.formatted(couponEventId);
    }

    public enum IssueResult {
        SUCCESS,
        ALREADY_ISSUED,
        SOLD_OUT,
        FAILED
    }
}

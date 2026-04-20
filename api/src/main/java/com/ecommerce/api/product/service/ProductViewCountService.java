package com.ecommerce.api.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProductViewCountService {

    private static final String DELTA_KEY_PREFIX = "product:view-count:delta:";
    private static final String DIRTY_SET_KEY = "product:view-count:dirty";
    private static final long SCAN_COUNT = 1000L;

    private final StringRedisTemplate redisTemplate;
    private final ProductViewCountFlushService flushService;

    public void increase(Long productId) {
        String productIdStr = productId.toString();

        redisTemplate.opsForValue().increment(DELTA_KEY_PREFIX + productIdStr);
        redisTemplate.opsForSet().add(DIRTY_SET_KEY, productIdStr);
    }

    @Scheduled(fixedDelay = 30_000)
    public void flushToDb() {
        ScanOptions options = ScanOptions.scanOptions()
                .count(SCAN_COUNT)
                .build();

        try (Cursor<String> cursor = redisTemplate.opsForSet().scan(DIRTY_SET_KEY, options)) {
            while (cursor.hasNext()) {
                flushOneSafely(cursor.next());
            }
        } catch (Exception e) {
            log.error("상품 조회수 dirty set 스캔 실패", e);
        }
    }

    private void flushOneSafely(String productIdStr) {
        String deltaKey = DELTA_KEY_PREFIX + productIdStr;

        String deltaStr = redisTemplate.opsForValue().getAndDelete(deltaKey);
        if (deltaStr == null || deltaStr.equals("0")) {
            removeDirtyIfNoDelta(productIdStr);
            return;
        }

        long delta = Long.parseLong(deltaStr);
        Long productId = Long.valueOf(productIdStr);

        try {
            flushService.flushOne(productId, delta);
            removeDirtyIfNoDelta(productIdStr);
        } catch (RuntimeException e) {
            // DB flush 실패 시 redis의 값 복구
            redisTemplate.opsForValue().increment(deltaKey, delta);
            redisTemplate.opsForSet().add(DIRTY_SET_KEY, productIdStr);
            log.error("상품 조회수 변경분 flush 실패. productId = {}, delta = {}", productId, delta, e);
        }
    }

    private void removeDirtyIfNoDelta(String productIdStr) {
        if (Boolean.FALSE.equals(redisTemplate.hasKey(DELTA_KEY_PREFIX + productIdStr))) {
            redisTemplate.opsForSet().remove(DIRTY_SET_KEY, productIdStr);
        }
    }
}

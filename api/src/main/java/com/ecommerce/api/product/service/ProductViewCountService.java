package com.ecommerce.api.product.service;

import com.ecommerce.api.product.config.ProductViewCountFlushProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProductViewCountService {

    private static final String DELTA_KEY_PREFIX = "product:view-count:delta:";
    private static final String DIRTY_SET_KEY = "product:view-count:dirty";

    private final StringRedisTemplate redisTemplate;
    private final ProductViewCountFlushService flushService;
    private final ProductViewCountFlushProperties flushProperties;

    public void increase(Long productId) {
        String productIdStr = productId.toString();

        redisTemplate.opsForValue().increment(DELTA_KEY_PREFIX + productIdStr);
        redisTemplate.opsForSet().add(DIRTY_SET_KEY, productIdStr);
    }

    @Scheduled(fixedDelay = 30_000)
    public void flushToDb() {
        for (int i = 0; i < flushProperties.maxBatchesPerRun(); i++) {
            List<String> productIdList = redisTemplate.opsForSet()
                    .pop(DIRTY_SET_KEY, flushProperties.batchSize());

            if (productIdList == null || productIdList.isEmpty()) {
                return;
            }

            boolean success = flushBatchSafely(productIdList);
            if (!success) {
                return;
            }
        }
    }

    private boolean flushBatchSafely(List<String> productIdList) {
        Map<Long, Long> deltaByProductId = new TreeMap<>();

        try {
            collectDeltas(productIdList, deltaByProductId);

            if (deltaByProductId.isEmpty()) {
                return true;
            }

            flushService.flushAll(deltaByProductId);
            return true;
        } catch (RuntimeException e) {
            restoreDeltas(deltaByProductId);
            log.error(
                    "상품 조회수 변경분 flush 실패. productCount = {}, totalDelta = {}",
                    deltaByProductId.size(),
                    sumDeltas(deltaByProductId),
                    e
            );
            return false;
        }
    }

    private void collectDeltas(List<String> productIdList, Map<Long, Long> deltaByProductId) {
        for (String productIdStr : productIdList) {
            String deltaStr = redisTemplate.opsForValue()
                    .getAndDelete(DELTA_KEY_PREFIX + productIdStr);

            if (deltaStr == null || deltaStr.equals("0")) {
                continue;
            }

            deltaByProductId.put(
                    Long.valueOf(productIdStr),
                    Long.parseLong(deltaStr)
            );
        }
    }

    private void restoreDeltas(Map<Long, Long> deltaByProductId) {
        deltaByProductId.forEach((productId, delta) -> {
            String productIdStr = productId.toString();
            redisTemplate.opsForValue().increment(DELTA_KEY_PREFIX + productIdStr, delta);
            redisTemplate.opsForSet().add(DIRTY_SET_KEY, productIdStr);
        });
    }

    private long sumDeltas(Map<Long, Long> deltaByProductId) {
        return deltaByProductId.values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }
}

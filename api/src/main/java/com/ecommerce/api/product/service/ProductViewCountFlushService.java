package com.ecommerce.api.product.service;

import com.ecommerce.api.product.repository.ProductStatJdbcRepository;
import com.ecommerce.api.product.repository.ProductStatJdbcRepository.IncreaseViewCountCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class ProductViewCountFlushService {

    private final ProductStatJdbcRepository productStatJdbcRepository;

    @Transactional
    public void flushAll(Map<Long, Long> deltaByProductId) {
        if (deltaByProductId == null || deltaByProductId.isEmpty()) {
            return;
        }

        List<IncreaseViewCountCommand> commands = deltaByProductId.entrySet().stream()
                .map(entry -> new IncreaseViewCountCommand(entry.getKey(), entry.getValue()))
                .toList();

        productStatJdbcRepository.increaseViewCounts(commands);
    }
}

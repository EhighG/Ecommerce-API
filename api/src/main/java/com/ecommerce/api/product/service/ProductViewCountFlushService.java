package com.ecommerce.api.product.service;

import com.ecommerce.api.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ProductViewCountFlushService {

    private final ProductRepository productRepository;

    @Transactional
    public void flushOne(Long productId, long delta) {
        productRepository.increaseViewCount(productId, delta);
    }
}

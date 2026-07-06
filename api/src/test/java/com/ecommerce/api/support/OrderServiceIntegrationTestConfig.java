package com.ecommerce.api.support;

import com.ecommerce.api.common.config.QuerydslConfig;
import com.ecommerce.api.coupon.repository.CouponEventRepository;
import com.ecommerce.api.coupon.repository.CouponIssuedRepository;
import com.ecommerce.api.coupon.service.CouponEventCacheService;
import com.ecommerce.api.coupon.service.CouponService;
import com.ecommerce.api.coupon.service.OrderItemCouponService;
import com.ecommerce.api.idempotency.service.IdempotencyService;
import com.ecommerce.api.idempotency.support.IdempotencyKeyValidator;
import com.ecommerce.api.inventory.repository.InventoryJdbcRepository;
import com.ecommerce.api.inventory.service.InventoryService;
import com.ecommerce.api.media.service.MediaService;
import com.ecommerce.api.order.repository.OrderItemRepository;
import com.ecommerce.api.order.service.IdempotentOrderPlacementService;
import com.ecommerce.api.order.service.OrderItemService;
import com.ecommerce.api.order.service.OrderPlacementService;
import com.ecommerce.api.order.support.OrderRequestFingerprintGenerator;
import com.ecommerce.api.product.repository.ProductStatJdbcRepository;
import com.ecommerce.api.product.service.ProductDeletionService;
import com.ecommerce.api.product.support.ProductImageUrlResolver;
import com.ecommerce.api.review.service.ReviewService;
import com.ecommerce.api.user.repository.UserRepository;
import com.ecommerce.api.user.service.UserService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.mock;

@TestConfiguration
@Import({
        QuerydslConfig.class,
        IdempotentOrderPlacementService.class,
        OrderPlacementService.class,
        OrderItemService.class,
        IdempotencyService.class,
        IdempotencyKeyValidator.class,
        OrderRequestFingerprintGenerator.class,
        InventoryService.class,
        InventoryJdbcRepository.class,
        OrderItemCouponService.class,
        ProductStatJdbcRepository.class,
        ProductImageUrlResolver.class
})
public class OrderServiceIntegrationTestConfig {

    @Bean
    UserService userService(UserRepository userRepository, OrderItemRepository orderItemRepository) {
        return new UserService(
                userRepository,
                testPasswordEncoder(),
                orderItemRepository,
                mock(ProductDeletionService.class),
                mock(ReviewService.class)
        );
    }

    @Bean
    CouponService couponService(CouponEventRepository couponEventRepository,
                                CouponIssuedRepository couponIssuedRepository,
                                UserRepository userRepository) {
        return new CouponService(
                couponEventRepository,
                couponIssuedRepository,
                userRepository,
                mock(CouponEventCacheService.class)
        );
    }

    @Bean
    MediaService mediaService() {
        return mock(MediaService.class);
    }

    private PasswordEncoder testPasswordEncoder() {
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return rawPassword.toString();
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return rawPassword.toString().equals(encodedPassword);
            }
        };
    }
}

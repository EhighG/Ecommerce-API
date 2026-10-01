package com.ecommerce.api.support;

import com.ecommerce.api.cartitem.service.CartItemService;
import com.ecommerce.api.coupon.service.CouponService;
import com.ecommerce.api.inventory.service.InventoryService;
import com.ecommerce.api.media.service.MediaService;
import com.ecommerce.api.order.service.IdempotentOrderPlacementService;
import com.ecommerce.api.order.service.OrderItemService;
import com.ecommerce.api.order.service.OrderService;
import com.ecommerce.api.product.service.ProductService;
import com.ecommerce.api.review.service.ReviewService;
import com.ecommerce.api.user.service.UserService;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 컨트롤러 계층만 띄우는 테스트의 공통 설정. 서비스는 mock이고 DB, Redis, GCS는 쓰지 않는다.
 * springdoc 자동 설정과 이름에 {@code OpenApi}가 들어간 설정 클래스를 함께 올려서, local 서버와 같은 명세를 만든다.
 * 컨트롤러가 새 서비스에 의존하면 여기에 mock을 추가한다.
 */
@WebMvcTest(
        properties = "springdoc.api-docs.enabled=true",
        includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*OpenApi.*")
)
@AutoConfigureMockMvc(addFilters = false)
@ImportAutoConfiguration({
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class,
        SpringDocSecurityConfiguration.class
})
public abstract class WebLayerTestSupport {

    // 메인 클래스의 @EnableJpaAuditing이 웹 슬라이스에서도 JPA 매핑 컨텍스트를 찾는다
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean CartItemService cartItemService;
    @MockitoBean CouponService couponService;
    @MockitoBean InventoryService inventoryService;
    @MockitoBean MediaService mediaService;
    @MockitoBean OrderService orderService;
    @MockitoBean IdempotentOrderPlacementService idempotentOrderPlacementService;
    @MockitoBean OrderItemService orderItemService;
    @MockitoBean ProductService productService;
    @MockitoBean ReviewService reviewService;
    @MockitoBean UserService userService;
}

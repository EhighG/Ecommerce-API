package com.ecommerce.api.order.service;

import com.ecommerce.api.cartitem.entity.CartItem;
import com.ecommerce.api.inventory.entity.Inventory;
import com.ecommerce.api.order.dto.OrderDetailRes;
import com.ecommerce.api.order.dto.OrderItemListRes;
import com.ecommerce.api.order.dto.OrderReq;
import com.ecommerce.api.product.entity.Product;
import com.ecommerce.api.product.entity.ProductStat;
import com.ecommerce.api.support.OrderServiceIntegrationTestSupport;
import com.ecommerce.api.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(OrderService.class)
class OrderServiceDetailTest extends OrderServiceIntegrationTestSupport {

    @Autowired
    private IdempotentOrderPlacementService idempotentOrderPlacementService;

    @Autowired
    private OrderItemService orderItemService;

    @Autowired
    private OrderService orderService;

    @Test
    @DisplayName("주문 상세는 항목을 ID 오름차순으로 주고, 항목마다 주문 시점 단가를 준다")
    void getMyOrderDetail_returnsItemsInIdOrderWithUnitPrice() {
        // given
        OrderFixture fixture = createOrderFixtureWithoutCoupon(10, 1);
        Long secondCartItemId = addCartItemWithNewProduct(fixture, 20_000L);
        Long orderId = placeOrder(fixture.buyerId(), secondCartItemId, fixture.cartItemId());

        // when
        OrderDetailRes detail = orderService.getMyOrderDetail(orderId, fixture.buyerId());

        // then
        List<Long> itemIds = detail.itemList().stream().map(OrderItemListRes::orderItemId).toList();
        assertThat(itemIds).hasSize(2).isSorted();
        assertThat(detail.itemList())
                .extracting(item -> item.product().unitPrice())
                .containsExactlyInAnyOrder(10_000L, 20_000L);
    }

    @Test
    @DisplayName("항목을 취소하면 주문 상세의 현재 금액은 줄고 주문 총액은 그대로다")
    void getMyOrderDetail_afterCancel_currentTotalPriceExcludesCanceledItem() {
        // given
        OrderFixture fixture = createOrderFixtureWithoutCoupon(10, 1);
        Long secondCartItemId = addCartItemWithNewProduct(fixture, 20_000L);
        Long orderId = placeOrder(fixture.buyerId(), fixture.cartItemId(), secondCartItemId);
        OrderItemListRes cheaperItem = orderService.getMyOrderDetail(orderId, fixture.buyerId()).itemList().stream()
                .filter(item -> item.product().unitPrice() == 10_000L)
                .findFirst()
                .orElseThrow();

        // when
        orderItemService.cancel(cheaperItem.orderItemId(), fixture.buyerId());
        OrderDetailRes detail = orderService.getMyOrderDetail(orderId, fixture.buyerId());

        // then
        assertThat(detail.totalPrice()).isEqualTo(30_000L);
        assertThat(detail.currentTotalPrice()).isEqualTo(20_000L);
    }

    private Long placeOrder(Long buyerId, Long... cartItemIds) {
        List<OrderReq.OrderItemReq> items = Arrays.stream(cartItemIds)
                .map(cartItemId -> new OrderReq.OrderItemReq(cartItemId, 1, null))
                .toList();
        return idempotentOrderPlacementService.placeOrder(new OrderReq(items), buyerId, UUID.randomUUID().toString());
    }

    private Long addCartItemWithNewProduct(OrderFixture fixture, long unitPrice) {
        return tx(() -> {
            Product existing = productRepository.findById(fixture.productId()).orElseThrow();
            User buyer = userRepository.findById(fixture.buyerId()).orElseThrow();
            Product product = productRepository.save(Product.register(
                    existing.getName() + "-2",
                    existing.getCategory(),
                    "두 번째 테스트 상품입니다.",
                    unitPrice,
                    existing.getSeller()
            ));
            productStatRepository.save(new ProductStat(product));
            inventoryRepository.save(new Inventory(product, 10));
            return cartItemRepository.save(new CartItem(buyer, product, 1)).getId();
        });
    }
}

package com.ecommerce.api.order.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.order.dto.OrderReq;
import com.ecommerce.api.order.entity.OrderItem;
import com.ecommerce.api.order.enums.OrderStatus;
import com.ecommerce.api.support.OrderServiceIntegrationTestSupport;
import com.ecommerce.api.user.entity.User;
import com.ecommerce.api.user.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static com.ecommerce.api.common.exception.ErrorCode.ORDER_ACCESS_DENIED;
import static com.ecommerce.api.common.exception.ErrorCode.WRONG_STATUS_CHANGE;
import static com.ecommerce.api.coupon.enums.CouponStatus.ISSUED;
import static com.ecommerce.api.coupon.enums.CouponStatus.USED;
import static com.ecommerce.api.order.enums.OrderStatus.CANCELED;
import static com.ecommerce.api.order.enums.OrderStatus.ORDERED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderItemServiceCancelTest extends OrderServiceIntegrationTestSupport {

    @Autowired
    private OrderPlacementService orderPlacementService;

    @Autowired
    private OrderItemService orderItemService;

    @Test
    @DisplayName("주문완료 상태의 주문항목을 취소하면 상태를 변경하고 복구 부수효과를 실행한다")
    void givenOrderedOrderItem_whenCancel_thenCancelAndRestoreSideEffects() {
        // given
        PlacedOrder placedOrder = placeOrderWithCoupon();

        assertThat(orderItemStatus(placedOrder.orderItemId())).isEqualTo(ORDERED);
        assertThat(inventoryQuantity(placedOrder.fixture().productId())).isEqualTo(9);
        assertThat(productOrderItemCount(placedOrder.fixture().productId())).isEqualTo(1);
        assertThat(couponStatus(placedOrder.fixture().couponIssuedId())).isEqualTo(USED);

        // when
        orderItemService.cancel(placedOrder.orderItemId(), placedOrder.fixture().buyerId());

        // then
        assertThat(orderItemStatus(placedOrder.orderItemId())).isEqualTo(CANCELED);
        assertThat(inventoryQuantity(placedOrder.fixture().productId())).isEqualTo(10);
        assertThat(productOrderItemCount(placedOrder.fixture().productId())).isZero();
        assertThat(couponStatus(placedOrder.fixture().couponIssuedId())).isEqualTo(ISSUED);
    }

    @Test
    @DisplayName("이미 취소된 주문항목을 다시 취소하면 성공 처리하고 복구 부수효과를 반복하지 않는다")
    void givenAlreadyCanceledOrderItem_whenCancelAgain_thenSucceedWithoutRepeatedSideEffects() {
        // given
        PlacedOrder placedOrder = placeOrderWithCoupon();
        orderItemService.cancel(placedOrder.orderItemId(), placedOrder.fixture().buyerId());

        long orderItemCouponCount = orderItemCouponRepository.count();
        int inventoryQuantity = inventoryQuantity(placedOrder.fixture().productId());
        long productOrderItemCount = productOrderItemCount(placedOrder.fixture().productId());

        // when
        orderItemService.cancel(placedOrder.orderItemId(), placedOrder.fixture().buyerId());

        // then
        assertThat(orderItemStatus(placedOrder.orderItemId())).isEqualTo(CANCELED);
        assertThat(inventoryQuantity(placedOrder.fixture().productId())).isEqualTo(inventoryQuantity);
        assertThat(productOrderItemCount(placedOrder.fixture().productId())).isEqualTo(productOrderItemCount);
        assertThat(couponStatus(placedOrder.fixture().couponIssuedId())).isEqualTo(ISSUED);
        assertThat(orderItemCouponRepository.count()).isEqualTo(orderItemCouponCount);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(InvalidCancelStatus.class)
    @DisplayName("취소 불가능 상태의 주문항목을 취소하면 상태 변경 오류를 반환한다")
    void givenNonCancelableStatus_whenCancel_thenThrowWrongStatusChange(InvalidCancelStatus invalidStatus) {
        // given
        PlacedOrder placedOrder = placeOrderWithCoupon();
        invalidStatus.changeStatus(orderItemService, placedOrder);

        // when
        AppException exception = assertThrows(AppException.class,
                () -> orderItemService.cancel(placedOrder.orderItemId(), placedOrder.fixture().buyerId()));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(WRONG_STATUS_CHANGE);
        assertThat(orderItemStatus(placedOrder.orderItemId())).isEqualTo(invalidStatus.status);
        assertThat(inventoryQuantity(placedOrder.fixture().productId())).isEqualTo(9);
        assertThat(productOrderItemCount(placedOrder.fixture().productId())).isEqualTo(1);
        assertThat(couponStatus(placedOrder.fixture().couponIssuedId())).isEqualTo(USED);
    }

    @Test
    @DisplayName("구매자나 판매자가 아닌 사용자가 주문항목을 취소하면 권한 오류를 반환한다")
    void givenUnauthorizedUser_whenCancel_thenThrowAccessDenied() {
        // given
        PlacedOrder placedOrder = placeOrderWithCoupon();
        Long strangerId = createStrangerUser();

        // when
        AppException exception = assertThrows(AppException.class,
                () -> orderItemService.cancel(placedOrder.orderItemId(), strangerId));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ORDER_ACCESS_DENIED);
        assertThat(orderItemStatus(placedOrder.orderItemId())).isEqualTo(ORDERED);
        assertThat(inventoryQuantity(placedOrder.fixture().productId())).isEqualTo(9);
        assertThat(productOrderItemCount(placedOrder.fixture().productId())).isEqualTo(1);
        assertThat(couponStatus(placedOrder.fixture().couponIssuedId())).isEqualTo(USED);
    }

    private PlacedOrder placeOrderWithCoupon() {
        OrderFixture fixture = createOrderFixtureWithCoupon(10, 2);
        OrderReq req = orderReq(fixture, 1);
        Long orderId = orderPlacementService.placeOrder(req, fixture.buyerId());
        OrderItem orderItem = getOnlyOrderItem(orderId);

        return new PlacedOrder(fixture, orderId, orderItem.getId());
    }

    private Long createStrangerUser() {
        return tx(() -> userRepository.save(User.join(
                "stranger-" + UUID.randomUUID().toString().substring(0, 8) + "@e.com",
                "stranger",
                "password",
                UserRole.BUYER
        )).getId());
    }

    private record PlacedOrder(OrderFixture fixture, Long orderId, Long orderItemId) {
    }

    private enum InvalidCancelStatus {
        SHIPPED("배송중", OrderStatus.SHIPPED) {
            @Override
            void changeStatus(OrderItemService orderItemService, PlacedOrder placedOrder) {
                orderItemService.ship(placedOrder.orderItemId(), placedOrder.fixture().sellerId());
            }
        },
        DELIVERED("배송완료", OrderStatus.DELIVERED) {
            @Override
            void changeStatus(OrderItemService orderItemService, PlacedOrder placedOrder) {
                orderItemService.ship(placedOrder.orderItemId(), placedOrder.fixture().sellerId());
                orderItemService.deliver(placedOrder.orderItemId(), placedOrder.fixture().sellerId());
            }
        },
        PURCHASE_CONFIRMED("구매확정", OrderStatus.PURCHASE_CONFIRMED) {
            @Override
            void changeStatus(OrderItemService orderItemService, PlacedOrder placedOrder) {
                orderItemService.ship(placedOrder.orderItemId(), placedOrder.fixture().sellerId());
                orderItemService.deliver(placedOrder.orderItemId(), placedOrder.fixture().sellerId());
                orderItemService.confirm(placedOrder.orderItemId(), placedOrder.fixture().buyerId());
            }
        };

        private final String displayName;
        private final OrderStatus status;

        InvalidCancelStatus(String displayName, OrderStatus status) {
            this.displayName = displayName;
            this.status = status;
        }

        abstract void changeStatus(OrderItemService orderItemService, PlacedOrder placedOrder);

        @Override
        public String toString() {
            return displayName;
        }
    }
}

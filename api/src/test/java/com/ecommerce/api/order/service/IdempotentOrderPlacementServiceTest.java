package com.ecommerce.api.order.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.idempotency.entity.IdempotencyRecord;
import com.ecommerce.api.idempotency.enums.IdempotencyResourceType;
import com.ecommerce.api.idempotency.enums.IdempotencyScope;
import com.ecommerce.api.order.dto.OrderReq;
import com.ecommerce.api.order.support.OrderRequestFingerprintGenerator;
import com.ecommerce.api.support.OrderServiceIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static com.ecommerce.api.common.exception.ErrorCode.*;
import static com.ecommerce.api.coupon.enums.CouponStatus.USED;
import static com.ecommerce.api.idempotency.enums.IdempotencyStatus.PROCESSING;
import static com.ecommerce.api.idempotency.enums.IdempotencyStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdempotentOrderPlacementServiceTest extends OrderServiceIntegrationTestSupport {

    @Autowired
    private IdempotentOrderPlacementService idempotentOrderPlacementService;

    @Autowired
    private OrderRequestFingerprintGenerator orderRequestFingerprintGenerator;

    @Test
    @DisplayName("주문 생성 최초 요청은 주문을 생성하고 멱등성 기록을 성공 상태로 저장한다")
    void givenValidOrderRequest_whenPlaceOrderFirstTime_thenCreateOrderAndSucceededRecord() {
        // given
        OrderFixture fixture = createOrderFixtureWithCoupon(10, 2);
        OrderReq req = orderReq(fixture, 1);
        String idempotencyKey = "order-create-first";

        // when
        Long orderId = idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), idempotencyKey);

        // then
        IdempotencyRecord record = idempotencyRecordRepository
                .findRecord(fixture.buyerId(), IdempotencyScope.ORDER_CREATE, idempotencyKey)
                .orElseThrow();

        assertThat(orderId).isNotNull();
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(record.getStatus()).isEqualTo(SUCCEEDED);
        assertThat(record.getResourceType()).isEqualTo(IdempotencyResourceType.ORDER);
        assertThat(record.getResourceId()).isEqualTo(orderId);
        assertThat(inventoryQuantity(fixture.productId())).isEqualTo(9);
        assertThat(productOrderItemCount(fixture.productId())).isEqualTo(1);
        assertThat(cartQuantity(fixture.cartItemId())).isEqualTo(1);
        assertThat(couponStatus(fixture.couponIssuedId())).isEqualTo(USED);
        assertThat(orderItemCouponRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키와 같은 요청으로 재시도하면 기존 주문 ID를 반환하고 부수효과를 반복하지 않는다")
    void givenSucceededOrder_whenPlaceOrderAgainWithSameKeyAndRequest_thenReplayExistingOrder() {
        // given
        OrderFixture fixture = createOrderFixtureWithCoupon(10, 2);
        OrderReq req = orderReq(fixture, 1);
        String idempotencyKey = "order-create-replay";
        Long firstOrderId = idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), idempotencyKey);

        // when
        Long replayedOrderId = idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), idempotencyKey);

        // then
        assertThat(replayedOrderId).isEqualTo(firstOrderId);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderItemRepository.count()).isEqualTo(1);
        assertThat(inventoryQuantity(fixture.productId())).isEqualTo(9);
        assertThat(productOrderItemCount(fixture.productId())).isEqualTo(1);
        assertThat(cartQuantity(fixture.cartItemId())).isEqualTo(1);
        assertThat(couponStatus(fixture.couponIssuedId())).isEqualTo(USED);
        assertThat(orderItemCouponRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키로 다른 주문 요청이 들어오면 충돌로 실패한다")
    void givenSucceededOrder_whenPlaceOrderWithSameKeyAndDifferentRequest_thenThrowConflict() {
        // given
        OrderFixture fixture = createOrderFixtureWithoutCoupon(10, 3);
        String idempotencyKey = "order-create-conflict";
        idempotentOrderPlacementService.placeOrder(orderReq(fixture, 1), fixture.buyerId(), idempotencyKey);
        OrderReq differentReq = orderReq(fixture, 2);

        // when
        AppException exception = assertThrows(AppException.class,
                () -> idempotentOrderPlacementService.placeOrder(differentReq, fixture.buyerId(), idempotencyKey));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(IDEMPOTENCY_KEY_CONFLICT);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(inventoryQuantity(fixture.productId())).isEqualTo(9);
        assertThat(productOrderItemCount(fixture.productId())).isEqualTo(1);
        assertThat(cartQuantity(fixture.cartItemId())).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 키의 요청이 처리 중이면 진행 중 충돌로 실패한다")
    void givenProcessingRecord_whenPlaceOrderWithSameKeyAndRequest_thenThrowProcessingConflict() {
        // given
        OrderFixture fixture = createOrderFixtureWithoutCoupon(10, 2);
        OrderReq req = orderReq(fixture, 1);
        String idempotencyKey = "order-create-processing";
        String fingerprint = orderRequestFingerprintGenerator.generate(req);

        tx(() -> idempotencyRecordRepository.saveAndFlush(IdempotencyRecord.processing(
                fixture.buyerId(),
                IdempotencyScope.ORDER_CREATE,
                idempotencyKey,
                fingerprint,
                Instant.now().plusSeconds(300)
        )));

        // when
        AppException exception = assertThrows(AppException.class,
                () -> idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), idempotencyKey));

        // then
        IdempotencyRecord record = idempotencyRecordRepository
                .findRecord(fixture.buyerId(), IdempotencyScope.ORDER_CREATE, idempotencyKey)
                .orElseThrow();

        assertThat(exception.getErrorCode()).isEqualTo(IDEMPOTENCY_REQUEST_PROCESSING);
        assertThat(record.getStatus()).isEqualTo(PROCESSING);
        assertThat(orderRepository.count()).isZero();
        assertThat(inventoryQuantity(fixture.productId())).isEqualTo(10);
        assertThat(cartQuantity(fixture.cartItemId())).isEqualTo(2);
    }

    @Test
    @DisplayName("주문 생성 본 로직이 실패하면 처리 중 멱등성 기록을 정리한다")
    void givenBusinessFailure_whenPlaceOrder_thenDeleteProcessingRecord() {
        // given
        OrderFixture fixture = createOrderFixtureWithoutCoupon(0, 1);
        OrderReq req = orderReq(fixture, 1);
        String idempotencyKey = "order-create-failure-cleanup";

        // when
        AppException exception = assertThrows(AppException.class,
                () -> idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), idempotencyKey));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(INSUFFICIENT_INVENTORY);
        assertThat(idempotencyRecordRepository
                .findRecord(fixture.buyerId(), IdempotencyScope.ORDER_CREATE, idempotencyKey)).isEmpty();
        assertThat(orderRepository.count()).isZero();
        assertThat(inventoryQuantity(fixture.productId())).isZero();
        assertThat(cartQuantity(fixture.cartItemId())).isEqualTo(1);
    }

    @Test
    @DisplayName("멱등성 키가 없으면 주문을 생성하지 않고 필수 헤더 오류를 반환한다")
    void givenMissingIdempotencyKey_whenPlaceOrder_thenThrowRequiredError() {
        // given
        OrderFixture fixture = createOrderFixtureWithoutCoupon(10, 2);
        OrderReq req = orderReq(fixture, 1);

        // when
        AppException exception = assertThrows(AppException.class,
                () -> idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), null));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(IDEMPOTENCY_KEY_REQUIRED);
        assertThat(orderRepository.count()).isZero();
        assertThat(inventoryQuantity(fixture.productId())).isEqualTo(10);
        assertThat(cartQuantity(fixture.cartItemId())).isEqualTo(2);
    }

    @Test
    @DisplayName("멱등성 키 형식이 잘못되면 주문을 생성하지 않고 형식 오류를 반환한다")
    void givenInvalidIdempotencyKey_whenPlaceOrder_thenThrowInvalidKeyError() {
        // given
        OrderFixture fixture = createOrderFixtureWithoutCoupon(10, 2);
        OrderReq req = orderReq(fixture, 1);
        String idempotencyKey = "invalid key";

        // when
        AppException exception = assertThrows(AppException.class,
                () -> idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), idempotencyKey));

        // then
        assertThat(exception.getErrorCode()).isEqualTo(IDEMPOTENCY_KEY_INVALID);
        assertThat(orderRepository.count()).isZero();
        assertThat(inventoryQuantity(fixture.productId())).isEqualTo(10);
        assertThat(cartQuantity(fixture.cartItemId())).isEqualTo(2);
    }

}

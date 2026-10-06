package com.ecommerce.api.order.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.idempotency.entity.IdempotencyRecord;
import com.ecommerce.api.idempotency.enums.IdempotencyResourceType;
import com.ecommerce.api.idempotency.enums.IdempotencyScope;
import com.ecommerce.api.idempotency.service.IdempotencyService;
import com.ecommerce.api.order.dto.OrderReq;
import com.ecommerce.api.order.support.OrderRequestFingerprintGenerator;
import com.ecommerce.api.support.OrderServiceIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.ecommerce.api.common.exception.ErrorCode.IDEMPOTENCY_REQUEST_PROCESSING;
import static com.ecommerce.api.idempotency.enums.IdempotencyStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 만료된 멱등성 record를 다루는 주문 생성 테스트다.
 * 경쟁 테스트는 단계 순서를 sleep이 아니라 MySQL의 락 대기 상태로 맞춘다.
 * 기대한 락 대기가 제한 시간 안에 생기지 않으면 테스트가 실패하므로, 통과했다면 목표 경로가 실제로 실행된 것이다.
 */
class IdempotentOrderPlacementExpiredRecordTest extends OrderServiceIntegrationTestSupport {

    private static final Duration WAIT_LIMIT = Duration.ofSeconds(15);

    @Autowired
    private IdempotentOrderPlacementService idempotentOrderPlacementService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private OrderRequestFingerprintGenerator orderRequestFingerprintGenerator;

    // 장바구니 수량 2, 주문 수량 1로 주문한다. 첫 번째 테스트(경쟁)에서는 이것이 필요하다.
    // 전량 주문이면 장바구니 항목이 지워져서, 고치기 전 코드에서 두 번째 주문이 7000으로 막히고 중복 주문이라는 버그가 드러나지 않는다.
    private static final int CART_QUANTITY = 2;
    private static final int ORDER_QUANTITY = 1;

    @Test
    @DisplayName("A가 성공 표시를 커밋하기 전에 B가 만료된 기록을 지우려고 기다리면, 커밋 뒤 B는 처리 중 충돌을 받고 주문은 1건이다")
    void givenSucceededMarkNotYetCommitted_whenRetryDeletesExpiredRecord_thenRetryGetsProcessingConflict() throws Exception {
        // given: 키 K로 주문 1건을 만들고, 그 record를 "처리 중, 만료됨"으로 되돌린다
        OrderFixture fixture = createOrderFixtureWithoutCoupon(10, CART_QUANTITY);
        OrderReq req = orderReq(fixture, ORDER_QUANTITY);
        String key = "expired-race-succeeded-mark";
        Long firstOrderId = idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), key);
        Long recordId = recordId(fixture, key);
        revertToExpiredProcessing(recordId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch markedSucceeded = new CountDownLatch(1);
        CountDownLatch commitSignal = new CountDownLatch(1);

        try {
            // when: A는 성공 표시(UPDATE)를 실행해 행 락을 잡은 채 커밋 신호를 기다린다
            Future<?> a = executor.submit(() -> tx(() -> {
                idempotencyService.markSucceeded(recordId, IdempotencyResourceType.ORDER, firstOrderId);
                markedSucceeded.countDown();
                awaitOrFail(commitSignal, "A의 커밋 신호");
            }));
            assertThat(markedSucceeded.await(WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS))
                    .as("A가 성공 표시를 실행해야 합니다.").isTrue();

            // B는 같은 키로 재시도한다. 락 없는 조회는 A가 커밋하기 전의 "처리 중, 만료됨"을 본다
            Future<Long> b = executor.submit(
                    () -> idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), key));

            // B가 만료된 기록의 DELETE에서 A의 행 락을 기다리는 것을 확인한 뒤 A가 커밋한다
            awaitLockWaiters("idempotency_record", 1, WAIT_LIMIT);
            commitSignal.countDown();

            // then: B의 DELETE는 0행이므로 처리 중 충돌(9103)이다. 9103은 이 경로에서만 나온다
            a.get(WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS);
            assertProcessingConflict(b);

            IdempotencyRecord record = idempotencyRecordRepository
                    .findRecord(fixture.buyerId(), IdempotencyScope.ORDER_CREATE, key).orElseThrow();
            assertThat(record.getId()).isEqualTo(recordId);
            assertThat(record.getStatus()).isEqualTo(SUCCEEDED);
            assertThat(record.getResourceId()).isEqualTo(firstOrderId);
            assertThat(orderRepository.count()).isEqualTo(1);
            assertThat(inventoryQuantity(fixture.productId())).isEqualTo(10 - ORDER_QUANTITY);
            assertThat(productOrderItemCount(fixture.productId())).isEqualTo(1);
            assertThat(cartQuantity(fixture.cartItemId())).isEqualTo(CART_QUANTITY - ORDER_QUANTITY);

            // 같은 키로 다시 보내면 1번 주문 ID를 받는다
            assertThat(idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), key))
                    .isEqualTo(firstOrderId);
            assertThat(orderRepository.count()).isEqualTo(1);
        } finally {
            commitSignal.countDown();
            stop(executor);
        }
    }

    @Test
    @DisplayName("B가 만료된 선점을 이어받으면, 뒤늦게 성공 표시를 시도한 A는 처리 중 충돌로 롤백되고 주문은 B의 1건이다")
    void givenRetryTookOverExpiredClaim_whenOriginalRequestMarksSucceeded_thenOriginalRollsBack() throws Exception {
        // given
        OrderFixture fixture = createOrderFixtureWithoutCoupon(10, CART_QUANTITY);
        OrderReq req = orderReq(fixture, ORDER_QUANTITY);
        String key = "expired-race-takeover";

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 재고 행을 잠가 A와 B의 본 처리를 재고 차감에서 멈춘다
        try (Connection inventoryLock = openRootConnection()) {
            lockInventory(inventoryLock, fixture.productId());

            try {
                // when: A는 선점을 커밋한 뒤 재고에서 기다린다
                Future<Long> a = executor.submit(
                        () -> idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), key));
                awaitLockWaiters("inventory", 1, WAIT_LIMIT);

                // A의 record 만료 시각을 과거로 바꾼다
                Long aRecordId = recordId(fixture, key);
                expire(aRecordId);

                // B는 만료된 기록을 지우고 새로 선점한 뒤 재고에서 기다린다
                Future<Long> b = executor.submit(
                        () -> idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), key));
                awaitLockWaiters("inventory", 2, WAIT_LIMIT);

                // A의 record는 이미 지워졌다. 재고 락을 풀면 누가 먼저 가도 결과가 같다
                inventoryLock.rollback();

                // then: A는 성공 표시가 0행이라 9103으로 롤백되고, B가 주문을 만든다
                assertProcessingConflict(a);
                Long bOrderId = b.get(WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS);

                IdempotencyRecord record = idempotencyRecordRepository
                        .findRecord(fixture.buyerId(), IdempotencyScope.ORDER_CREATE, key).orElseThrow();
                assertThat(record.getId()).isNotEqualTo(aRecordId);
                assertThat(record.getStatus()).isEqualTo(SUCCEEDED);
                assertThat(record.getResourceId()).isEqualTo(bOrderId);
                assertThat(orderRepository.count()).isEqualTo(1);
                assertThat(orderRepository.findAll().get(0).getId()).isEqualTo(bOrderId);
                assertThat(inventoryQuantity(fixture.productId())).isEqualTo(10 - ORDER_QUANTITY);
                assertThat(productOrderItemCount(fixture.productId())).isEqualTo(1);
                assertThat(cartQuantity(fixture.cartItemId())).isEqualTo(CART_QUANTITY - ORDER_QUANTITY);
            } finally {
                inventoryLock.rollback();
                stop(executor);
            }
        }
    }

    @Test
    @DisplayName("만료된 처리 중 기록이 있는 키로 요청하면 새로 선점해 주문을 만들고 기록은 성공 상태가 된다")
    void givenExpiredProcessingRecord_whenPlaceOrder_thenClaimAnewAndCreateOrder() {
        // given
        OrderFixture fixture = createOrderFixtureWithoutCoupon(10, CART_QUANTITY);
        OrderReq req = orderReq(fixture, ORDER_QUANTITY);
        String key = "expired-processing";
        Long staleRecordId = tx(() -> idempotencyRecordRepository.saveAndFlush(IdempotencyRecord.processing(
                fixture.buyerId(),
                IdempotencyScope.ORDER_CREATE,
                key,
                orderRequestFingerprintGenerator.generate(req),
                Instant.now().minusSeconds(1)
        )).getId());

        // when
        Long orderId = idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), key);

        // then
        IdempotencyRecord record = idempotencyRecordRepository
                .findRecord(fixture.buyerId(), IdempotencyScope.ORDER_CREATE, key).orElseThrow();
        assertThat(record.getId()).isNotEqualTo(staleRecordId);
        assertThat(record.getStatus()).isEqualTo(SUCCEEDED);
        assertThat(record.getResourceId()).isEqualTo(orderId);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(inventoryQuantity(fixture.productId())).isEqualTo(10 - ORDER_QUANTITY);
        assertThat(cartQuantity(fixture.cartItemId())).isEqualTo(CART_QUANTITY - ORDER_QUANTITY);
    }

    @Test
    @DisplayName("24시간이 지난 성공 기록이 있는 키로 요청하면 같은 키로 새 주문이 생기고 기록은 새 주문을 가리킨다")
    void givenExpiredSucceededRecord_whenPlaceOrder_thenCreateNewOrderWithSameKey() {
        // given
        OrderFixture fixture = createOrderFixtureWithoutCoupon(10, CART_QUANTITY);
        OrderReq req = orderReq(fixture, ORDER_QUANTITY);
        String key = "expired-succeeded";
        Long firstOrderId = idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), key);
        expire(recordId(fixture, key));

        // when
        Long secondOrderId = idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), key);

        // then
        IdempotencyRecord record = idempotencyRecordRepository
                .findRecord(fixture.buyerId(), IdempotencyScope.ORDER_CREATE, key).orElseThrow();
        assertThat(secondOrderId).isNotEqualTo(firstOrderId);
        assertThat(record.getStatus()).isEqualTo(SUCCEEDED);
        assertThat(record.getResourceId()).isEqualTo(secondOrderId);
        assertThat(orderRepository.count()).isEqualTo(2);
        assertThat(inventoryQuantity(fixture.productId())).isEqualTo(10 - 2 * ORDER_QUANTITY);
    }

    @Test
    @DisplayName("다른 트랜잭션이 만료된 기록을 먼저 지우는 동안 B가 기다리면, 커밋 뒤 B는 주문 상태 충돌이 아니라 처리 중 충돌을 받는다")
    void givenOtherTransactionDeletesExpiredRecord_whenRetryWaitsOnDelete_thenProcessingConflictNotOrderStatusConflict()
            throws Exception {
        // given
        OrderFixture fixture = createOrderFixtureWithoutCoupon(10, CART_QUANTITY);
        OrderReq req = orderReq(fixture, ORDER_QUANTITY);
        String key = "expired-race-concurrent-delete";
        Long recordId = tx(() -> idempotencyRecordRepository.saveAndFlush(IdempotencyRecord.processing(
                fixture.buyerId(),
                IdempotencyScope.ORDER_CREATE,
                key,
                orderRequestFingerprintGenerator.generate(req),
                Instant.now().minusSeconds(1)
        )).getId());

        ExecutorService executor = Executors.newSingleThreadExecutor();

        // 다른 트랜잭션이 만료된 기록을 지우고 커밋하지 않은 채 행 락을 쥔다
        try (Connection otherTx = openRootConnection()) {
            try {
                deleteRecord(otherTx, recordId);

                // when: B가 같은 키로 재시도하면 만료된 기록의 DELETE에서 기다린다
                Future<Long> b = executor.submit(
                        () -> idempotentOrderPlacementService.placeOrder(req, fixture.buyerId(), key));
                awaitLockWaiters("idempotency_record", 1, WAIT_LIMIT);
                otherTx.commit();

                // then: B의 DELETE는 0행이므로 처리 중 충돌(9103)이다. 9103은 이 경로에서만 나온다
                assertProcessingConflict(b);
                assertThat(idempotencyRecordRepository
                        .findRecord(fixture.buyerId(), IdempotencyScope.ORDER_CREATE, key)).isEmpty();
                assertThat(orderRepository.count()).isZero();
                assertThat(inventoryQuantity(fixture.productId())).isEqualTo(10);
                assertThat(cartQuantity(fixture.cartItemId())).isEqualTo(CART_QUANTITY);
            } finally {
                otherTx.rollback();
                stop(executor);
            }
        }
    }

    private Long recordId(OrderFixture fixture, String key) {
        return idempotencyRecordRepository
                .findRecord(fixture.buyerId(), IdempotencyScope.ORDER_CREATE, key)
                .orElseThrow()
                .getId();
    }

    // 만료 시각을 생성 시각으로 되돌린다. 두 컬럼을 모두 Hibernate가 썼으므로 시간대가 어긋나지 않는다.
    private void expire(Long recordId) {
        jdbcTemplate.update("update idempotency_record set expires_at = created_at where id = ?", recordId);
    }

    private void revertToExpiredProcessing(Long recordId) {
        jdbcTemplate.update("""
                update idempotency_record
                set status = 'PROCESSING', resource_type = null, resource_id = null, expires_at = created_at
                where id = ?
                """, recordId);
    }

    private void lockInventory(Connection connection, Long productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from inventory where product_id = ? for update")) {
            statement.setLong(1, productId);
            statement.executeQuery().close();
        }
    }

    private void deleteRecord(Connection connection, Long recordId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from idempotency_record where id = ?")) {
            statement.setLong(1, recordId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    // 실패한 테스트가 다음 테스트의 정리 단계까지 스레드를 남기지 않게 한다.
    private void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        if (!executor.awaitTermination(WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS)) {
            fail("테스트 스레드가 제한 시간 안에 종료되지 않았습니다.");
        }
    }

    private void awaitOrFail(CountDownLatch latch, String name) {
        try {
            if (!latch.await(WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS)) {
                fail(name + "를 제한 시간 안에 받지 못했습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(name + "를 기다리다 중단됐습니다.");
        }
    }

    private void assertProcessingConflict(Future<?> future) {
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> future.get(WAIT_LIMIT.toSeconds(), TimeUnit.SECONDS),
                "처리 중 충돌(9103)로 실패해야 하는데 요청이 성공했습니다.");

        assertThat(thrown.getCause())
                .as("처리 중 충돌(9103)이어야 합니다. 실제 예외 = %s", thrown.getCause())
                .isInstanceOfSatisfying(AppException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(IDEMPOTENCY_REQUEST_PROCESSING));
    }
}

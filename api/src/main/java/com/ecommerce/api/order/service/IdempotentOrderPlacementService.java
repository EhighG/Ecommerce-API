package com.ecommerce.api.order.service;

import com.ecommerce.api.idempotency.dto.IdempotencyClaimResult;
import com.ecommerce.api.idempotency.enums.IdempotencyResourceType;
import com.ecommerce.api.idempotency.enums.IdempotencyScope;
import com.ecommerce.api.idempotency.service.IdempotencyService;
import com.ecommerce.api.idempotency.support.IdempotencyKeyValidator;
import com.ecommerce.api.order.dto.OrderReq;
import com.ecommerce.api.order.support.OrderRequestFingerprintGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

@Slf4j
@RequiredArgsConstructor
@Service
public class IdempotentOrderPlacementService {

    private final OrderPlacementService orderPlacementService;
    private final IdempotencyService idempotencyService;
    private final IdempotencyKeyValidator idempotencyKeyValidator;
    private final OrderRequestFingerprintGenerator orderRequestFingerprintGenerator;
    private final PlatformTransactionManager transactionManager;

    public Long placeOrder(OrderReq req, Long userId, String idempotencyKey) {
        idempotencyKeyValidator.validate(idempotencyKey);

        String fingerprint = orderRequestFingerprintGenerator.generate(req);
        IdempotencyClaimResult claim = inspectOrClaim(userId, idempotencyKey, fingerprint);

        if (claim instanceof IdempotencyClaimResult.Replay replay) {
            return replayOrder(replay);
        }

        // IdempotencyRecord가 잘 생성된 경우
        if (claim instanceof IdempotencyClaimResult.Claimed(Long recordId)) {
            return placeNewOrder(req, userId, recordId);
        }

        throw new IllegalStateException("잘못된 idempotency claim result: " + claim);
    }

    private IdempotencyClaimResult inspectOrClaim(Long userId, String idempotencyKey, String fingerprint) {
        try {
            return executeInNewTransaction(status -> idempotencyService.inspectOrClaim(
                    userId, IdempotencyScope.ORDER_CREATE, idempotencyKey, fingerprint
            ));
        } catch (DataIntegrityViolationException e) { // 같은 요청이 동시에 들어올 때
            return executeInNewTransaction(status -> idempotencyService.inspectOrClaim(
                    userId, IdempotencyScope.ORDER_CREATE, idempotencyKey, fingerprint
            ));
        }
    }

    private Long replayOrder(IdempotencyClaimResult.Replay replay) {
        if (replay.resourceType() != IdempotencyResourceType.ORDER) {
            throw new IllegalStateException("잘못된 주문생성 replay resource type : " + replay.resourceType());
        }

        return Objects.requireNonNull(replay.resourceId());
    }

    private Long placeNewOrder(OrderReq req, Long userId, Long recordId) {
        try {
            return executeInNewTransaction(status -> {
                Long orderId = orderPlacementService.placeOrder(req, userId);
                idempotencyService.markSucceeded(recordId, IdempotencyResourceType.ORDER, orderId);

                return orderId;
            });
        } catch (RuntimeException e) {
            cleanupProcessing(recordId, e);
            throw e;
        }
    }

    private <T> T executeInNewTransaction(TransactionCallback<T> callback) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
        return transactionTemplate.execute(callback);
    }

    private void cleanupProcessing(Long recordId, RuntimeException originalException) {
        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);

            transactionTemplate.executeWithoutResult(status ->
                    idempotencyService.deleteProcessing(recordId)
            );
        } catch (RuntimeException cleanupException) {
            log.warn("PROCESSING IdempotencyRecord cleanup 실패. recordId = {}", recordId, cleanupException);
            originalException.addSuppressed(cleanupException);
        }
    }
}

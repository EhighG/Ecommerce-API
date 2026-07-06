package com.ecommerce.api.idempotency.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.idempotency.dto.IdempotencyClaimResult;
import com.ecommerce.api.idempotency.entity.IdempotencyRecord;
import com.ecommerce.api.idempotency.enums.IdempotencyResourceType;
import com.ecommerce.api.idempotency.enums.IdempotencyScope;
import com.ecommerce.api.idempotency.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static com.ecommerce.api.common.exception.ErrorCode.IDEMPOTENCY_KEY_CONFLICT;
import static com.ecommerce.api.common.exception.ErrorCode.IDEMPOTENCY_REQUEST_PROCESSING;
import static com.ecommerce.api.idempotency.enums.IdempotencyStatus.PROCESSING;
import static com.ecommerce.api.idempotency.enums.IdempotencyStatus.SUCCEEDED;

@RequiredArgsConstructor
@Service
public class IdempotencyService {

    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5);
    private static final Duration SUCCEEDED_TTL = Duration.ofHours(24);

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public IdempotencyClaimResult inspectOrClaim(Long userId, IdempotencyScope scope, String idempotencyKey,
                                                 String requestFingerprint) {
        Instant now = Instant.now();

        return idempotencyRecordRepository.findRecord(userId, scope, idempotencyKey)
                .map(record -> handleExisting(record, userId, scope, idempotencyKey,
                        requestFingerprint, now))
                .orElseGet(() -> createProcessing(userId, scope, idempotencyKey, requestFingerprint, now));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markSucceeded(Long recordId, IdempotencyResourceType resourceType, Long resourceId) {
        Instant now = Instant.now();

        Objects.requireNonNull(resourceType);
        Objects.requireNonNull(resourceId);

        int updated = idempotencyRecordRepository.markSucceeded(recordId, PROCESSING, SUCCEEDED,
                resourceType, resourceId, now.plus(SUCCEEDED_TTL), now);

        if (updated != 1) {
            throw new IllegalStateException("PROCESSING 상태인 IdempotencyRecord를 찾을 수 없습니다. recordId = " + recordId);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteProcessing(Long recordId) {
        idempotencyRecordRepository.deleteProcessing(recordId, PROCESSING);
    }

    private IdempotencyClaimResult handleExisting(IdempotencyRecord record, Long userId, IdempotencyScope scope,
                                                  String idempotencyKey, String requestFingerprint, Instant now) {
        if (record.isExpired(now)) {
            idempotencyRecordRepository.delete(record);
            idempotencyRecordRepository.flush();
            return createProcessing(userId, scope, idempotencyKey, requestFingerprint, now);
        }

        if (!record.hasSameFingerprint(requestFingerprint)) {
            throw new AppException(IDEMPOTENCY_KEY_CONFLICT);
        }

        if (record.isProcessing()) {
            throw new AppException(IDEMPOTENCY_REQUEST_PROCESSING);
        }

        if (record.isSucceeded()) {
            return new IdempotencyClaimResult.Replay(
                    Objects.requireNonNull(record.getResourceType()),
                    Objects.requireNonNull(record.getResourceId())
            );
        }

        throw new IllegalStateException("잘못된 Idempotency Status: " + record.getStatus());
    }

    private IdempotencyClaimResult.Claimed createProcessing(
            Long userId,
            IdempotencyScope scope,
            String idempotencyKey,
            String requestFingerprint,
            Instant now
    ) {
        IdempotencyRecord record = IdempotencyRecord.processing(
                userId,
                scope,
                idempotencyKey,
                requestFingerprint,
                now.plus(PROCESSING_TTL)
        );

        IdempotencyRecord saved = idempotencyRecordRepository.saveAndFlush(record);
        return new IdempotencyClaimResult.Claimed(saved.getId());
    }
}

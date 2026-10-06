package com.ecommerce.api.idempotency.service;

import com.ecommerce.api.common.exception.AppException;
import com.ecommerce.api.idempotency.dto.IdempotencyClaimResult;
import com.ecommerce.api.idempotency.entity.IdempotencyRecord;
import com.ecommerce.api.idempotency.enums.IdempotencyResourceType;
import com.ecommerce.api.idempotency.enums.IdempotencyScope;
import com.ecommerce.api.idempotency.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
            // 다른 요청이 만료된 선점을 이어받아 이 record를 지웠다. 이 요청의 본 처리는 롤백된다.
            log.warn("PROCESSING IdempotencyRecord 성공 표시가 0행이어서 처리 중 충돌로 응답합니다. recordId = {}", recordId);
            throw new AppException(IDEMPOTENCY_REQUEST_PROCESSING);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteProcessing(Long recordId) {
        idempotencyRecordRepository.deleteProcessing(recordId, PROCESSING);
    }

    private IdempotencyClaimResult handleExisting(IdempotencyRecord record, Long userId, IdempotencyScope scope,
                                                  String idempotencyKey, String requestFingerprint, Instant now) {
        if (record.isExpired(now)) {
            // 조회는 스냅샷이고 DELETE는 락을 잡은 뒤 최신 값에 실행된다. 만료 조건을 걸어, 그사이 성공으로 바뀐 기록은 지우지 않는다.
            // 0행이면 같은 트랜잭션에서 다시 읽지 않는다(REPEATABLE READ라 처음 스냅샷이 보인다). 클라이언트의 재시도에 맡긴다.
            if (idempotencyRecordRepository.deleteExpired(record.getId(), now) == 0) {
                log.warn("만료된 IdempotencyRecord 삭제가 0행이어서 처리 중 충돌로 응답합니다. userId = {}, recordId = {}, 조회 때 상태 = {}",
                        userId, record.getId(), record.getStatus());
                throw new AppException(IDEMPOTENCY_REQUEST_PROCESSING);
            }
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

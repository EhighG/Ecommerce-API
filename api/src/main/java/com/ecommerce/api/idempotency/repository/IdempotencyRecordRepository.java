package com.ecommerce.api.idempotency.repository;

import com.ecommerce.api.idempotency.entity.IdempotencyRecord;
import com.ecommerce.api.idempotency.enums.IdempotencyResourceType;
import com.ecommerce.api.idempotency.enums.IdempotencyScope;
import com.ecommerce.api.idempotency.enums.IdempotencyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Query("""
            select ir
            from IdempotencyRecord ir
            where ir.userId = :userId
                and ir.scope = :scope
                and ir.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRecord> findRecord(Long userId, IdempotencyScope scope, String idempotencyKey);

    @Modifying
    @Query("""
            update IdempotencyRecord ir
            set ir.status = :succeeded,
                ir.resourceType = :resourceType,
                ir.resourceId = :resourceId,
                ir.expiresAt = :expiresAt,
                ir.updatedAt = :updatedAt
            where ir.id = :recordId
                and ir.status = :processing
            """)
    int markSucceeded(Long recordId, IdempotencyStatus processing, IdempotencyStatus succeeded,
                      IdempotencyResourceType resourceType, Long resourceId, Instant expiresAt, Instant updatedAt);

    @Modifying
    @Query("""
            delete from IdempotencyRecord ir
            where ir.id = :recordId
                and ir.status = :processing
            """)
    int deleteProcessing(Long recordId, IdempotencyStatus processing);
}

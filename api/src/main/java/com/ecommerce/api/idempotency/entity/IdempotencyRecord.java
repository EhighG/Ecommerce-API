package com.ecommerce.api.idempotency.entity;

import com.ecommerce.api.common.util.BaseTimeEntity;
import com.ecommerce.api.idempotency.enums.IdempotencyResourceType;
import com.ecommerce.api.idempotency.enums.IdempotencyScope;
import com.ecommerce.api.idempotency.enums.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "idempotency_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_idempotency_record_user_scope_key",
                columnNames = {"user_id", "scope", "idempotency_key"}
        )
)
public class IdempotencyRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdempotencyScope scope;

    @Column(nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdempotencyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private IdempotencyResourceType resourceType;

    private Long resourceId;

    @Column(nullable = false)
    private Instant expiresAt;

    public IdempotencyRecord(
            Long userId,
            IdempotencyScope scope,
            String idempotencyKey,
            String requestFingerprint,
            Instant expiresAt
    ) {
        this.userId = userId;
        this.scope = scope;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.status = IdempotencyStatus.PROCESSING;
        this.expiresAt = expiresAt;
    }

    public static IdempotencyRecord processing(
            Long userId,
            IdempotencyScope scope,
            String idempotencyKey,
            String requestFingerprint,
            Instant expiresAt
    ) {
        return new IdempotencyRecord(userId, scope, idempotencyKey, requestFingerprint, expiresAt);
    }

    public boolean hasSameFingerprint(String fingerprint) {
        return this.requestFingerprint.equals(fingerprint);
    }

    public boolean isProcessing() {
        return this.status == IdempotencyStatus.PROCESSING;
    }

    public boolean isSucceeded() {
        return this.status == IdempotencyStatus.SUCCEEDED;
    }

    public boolean isExpired(Instant now) {
        return !this.expiresAt.isAfter(now);
    }
//
//    public void succeed(IdempotencyResourceType resourceType, Long resourceId, Instant expiresAt) {
//        if (!isProcessing()) {
//            throw new IllegalStateException("잘못된 status 변경: "
//                    + this.status + " -> " + IdempotencyStatus.SUCCEEDED);
//        }
//
//        this.status = IdempotencyStatus.SUCCEEDED;
//        this.resourceType = Objects.requireNonNull(resourceType);
//        this.resourceId = Objects.requireNonNull(resourceId);
//        this.expiresAt = Objects.requireNonNull(expiresAt);
//    }
}

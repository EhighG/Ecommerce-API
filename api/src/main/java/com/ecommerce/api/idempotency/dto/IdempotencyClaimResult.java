package com.ecommerce.api.idempotency.dto;

import com.ecommerce.api.idempotency.enums.IdempotencyResourceType;

public sealed interface IdempotencyClaimResult permits IdempotencyClaimResult.Claimed, IdempotencyClaimResult.Replay {

    record Claimed(Long recordId) implements IdempotencyClaimResult {}

    record Replay(IdempotencyResourceType resourceType, Long resourceId) implements IdempotencyClaimResult {}
}

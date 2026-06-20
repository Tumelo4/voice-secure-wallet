package com.voicesecure.payments;

import java.util.Objects;
import java.util.UUID;

public record PaymentRequest(
        UUID sagaId,
        UUID idempotencyKey,
        UUID userId,
        UUID fromAccountId,
        UUID toAccountId,
        long amount,
        String currency,
        String traceId
) {
    public PaymentRequest {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(fromAccountId, "fromAccountId");
        Objects.requireNonNull(toAccountId, "toAccountId");
        Objects.requireNonNull(currency, "currency");
        traceId = traceId == null ? "" : traceId.trim();
        if (traceId.isEmpty()) {
            throw new PaymentException("trace id is required");
        }
        if (amount <= 0) {
            throw new PaymentException("payment amount must be positive");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new PaymentException("source and destination accounts must differ");
        }
    }
}

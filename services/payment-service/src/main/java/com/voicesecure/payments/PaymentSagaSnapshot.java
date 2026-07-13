package com.voicesecure.payments;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PaymentSagaSnapshot(
        UUID sagaId,
        UUID idempotencyKey,
        UUID userId,
        UUID fromAccountId,
        UUID toAccountId,
        long amount,
        String currency,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        long version,
        double fraudScore,
        AuthPolicy authPolicy,
        FallbackMethod fallbackMethod,
        PaymentSagaState state,
        List<PaymentSagaState> stateHistory,
        List<PaymentEvent> events
) {
    public PaymentSagaSnapshot {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(fromAccountId, "fromAccountId");
        Objects.requireNonNull(toAccountId, "toAccountId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(state, "state");
        if (version < 0) throw new PaymentException("saga version cannot be negative");
        stateHistory = List.copyOf(Objects.requireNonNull(stateHistory, "stateHistory"));
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        if (stateHistory.isEmpty()) {
            throw new PaymentException("state history cannot be empty");
        }
        if (stateHistory.get(stateHistory.size() - 1) != state) {
            throw new PaymentException("state history must end in current state");
        }
    }
}

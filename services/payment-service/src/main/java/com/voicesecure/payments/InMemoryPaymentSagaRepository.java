package com.voicesecure.payments;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryPaymentSagaRepository implements PaymentSagaRepository {
    private final Map<UUID, PaymentSaga> bySagaId = new LinkedHashMap<>();
    private final Map<UUID, PaymentSaga> byIdempotencyKey = new LinkedHashMap<>();

    @Override
    public synchronized Optional<PaymentSaga> findBySagaId(UUID sagaId) {
        return Optional.ofNullable(bySagaId.get(sagaId));
    }

    @Override
    public synchronized Optional<PaymentSaga> findByIdempotencyKey(UUID idempotencyKey) {
        return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey));
    }

    @Override
    public synchronized List<PaymentSaga> findNonTerminalUpdatedBefore(Instant cutoff) {
        return bySagaId.values().stream()
                .filter(saga -> !saga.isTerminal() && saga.updatedAt().isBefore(cutoff))
                .toList();
    }

    @Override
    public synchronized void save(PaymentSaga saga) {
        bySagaId.put(saga.sagaId(), saga);
        byIdempotencyKey.put(saga.idempotencyKey(), saga);
    }
}

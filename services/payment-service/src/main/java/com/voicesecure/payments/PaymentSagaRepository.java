package com.voicesecure.payments;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentSagaRepository {
    Optional<PaymentSaga> findBySagaId(UUID sagaId);

    Optional<PaymentSaga> findByIdempotencyKey(UUID idempotencyKey);

    List<PaymentSaga> findNonTerminalUpdatedBefore(Instant cutoff);

    PaymentSaga createIfAbsent(PaymentSaga saga);

    void save(PaymentSaga saga);
}

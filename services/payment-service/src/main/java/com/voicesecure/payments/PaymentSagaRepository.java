package com.voicesecure.payments;

import java.util.Optional;
import java.util.UUID;

public interface PaymentSagaRepository {
    Optional<PaymentSaga> findBySagaId(UUID sagaId);

    Optional<PaymentSaga> findByIdempotencyKey(UUID idempotencyKey);

    void save(PaymentSaga saga);
}


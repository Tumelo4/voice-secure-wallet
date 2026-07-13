package com.voicesecure.api;

import java.util.Optional;
import java.util.UUID;

public interface PaymentReferenceRegistry {
    String referenceFor(UUID sagaId, UUID customerId);
    Optional<RegisteredPayment> find(String paymentReference);

    record RegisteredPayment(UUID sagaId, UUID customerId) { }
}

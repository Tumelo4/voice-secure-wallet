package com.voicesecure.api;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPaymentReferenceRegistry implements PaymentReferenceRegistry {
    private final Map<UUID, String> referencesBySaga = new ConcurrentHashMap<>();
    private final Map<String, RegisteredPayment> paymentsByReference = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Override
    public String referenceFor(UUID sagaId, UUID customerId) {
        return referencesBySaga.computeIfAbsent(sagaId, ignored -> {
            byte[] entropy = new byte[12];
            random.nextBytes(entropy);
            String reference = "VSW-" + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
            paymentsByReference.put(reference, new RegisteredPayment(sagaId, customerId));
            return reference;
        });
    }

    @Override
    public Optional<RegisteredPayment> find(String paymentReference) {
        return Optional.ofNullable(paymentsByReference.get(paymentReference));
    }
}

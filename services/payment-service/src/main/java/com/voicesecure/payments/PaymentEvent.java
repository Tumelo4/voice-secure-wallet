package com.voicesecure.payments;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentEvent(
        UUID eventId,
        UUID sagaId,
        String eventType,
        Instant occurredAt,
        String traceId,
        String payload
) {
    public PaymentEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(payload, "payload");
        traceId = traceId == null ? "" : traceId.trim();
    }
}


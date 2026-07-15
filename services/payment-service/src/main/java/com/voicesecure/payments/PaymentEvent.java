package com.voicesecure.payments;

import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventEnvelopeFactory;
import com.voicesecure.events.EventTopic;
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

    public EventEnvelope toEnvelope() {
        return EventEnvelopeFactory.create(
                eventId,
                EventTopic.PAYMENTS,
                sagaId,
                "Payment",
                eventType,
                occurredAt,
                traceId,
                payload
        );
    }
}

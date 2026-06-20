package com.voicesecure.payments;

import java.time.Instant;
import java.util.UUID;

final class PaymentEventFactory {
    private PaymentEventFactory() {
    }

    static PaymentEvent create(UUID sagaId, String traceId, String eventType, String payload) {
        return new PaymentEvent(
                UUID.randomUUID(),
                sagaId,
                eventType,
                Instant.now(),
                traceId,
                payload == null ? "" : payload
        );
    }

    static String payload(String key, String value, UUID userId, UUID fromAccountId, UUID toAccountId) {
        return "{\"" + key + "\":\"" + escape(value) + "\",\"userId\":\"" + userId + "\",\"fromAccountId\":\""
                + fromAccountId + "\",\"toAccountId\":\"" + toAccountId + "\"}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

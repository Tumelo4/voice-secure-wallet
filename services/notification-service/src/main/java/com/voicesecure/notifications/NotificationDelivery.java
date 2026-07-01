package com.voicesecure.notifications;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NotificationDelivery(
        UUID deliveryId,
        UUID sourceEventId,
        String sourceEventType,
        NotificationChannel channel,
        String recipientRef,
        String traceId,
        String message,
        Instant createdAt
) {
    public NotificationDelivery {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(sourceEventType, "sourceEventType");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(recipientRef, "recipientRef");
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(createdAt, "createdAt");
        if (sourceEventType.isBlank()) {
            throw new NotificationException("source event type is required");
        }
        if (recipientRef.isBlank()) {
            throw new NotificationException("recipient reference is required");
        }
        if (traceId.isBlank()) {
            throw new NotificationException("trace id is required");
        }
        if (message.isBlank()) {
            throw new NotificationException("notification message is required");
        }
    }
}

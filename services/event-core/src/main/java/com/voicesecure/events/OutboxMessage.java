package com.voicesecure.events;

import java.time.Instant;
import java.util.Objects;

public record OutboxMessage(
        EventEnvelope envelope,
        Instant createdAt,
        Instant publishedAt,
        int publishAttempts,
        String lastError
) {
    public OutboxMessage(EventEnvelope envelope, Instant createdAt, Instant publishedAt) {
        this(envelope, createdAt, publishedAt, 0, "");
    }

    public OutboxMessage {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(createdAt, "createdAt");
        lastError = lastError == null ? "" : lastError;
        if (publishAttempts < 0) {
            throw new EventEnvelopeException("publish attempts cannot be negative");
        }
    }

    public OutboxMessage withPublishedAt(Instant newPublishedAt) {
        return new OutboxMessage(envelope, createdAt, Objects.requireNonNull(newPublishedAt, "newPublishedAt"), publishAttempts, "");
    }

    public OutboxMessage withFailure(String error) {
        return new OutboxMessage(envelope, createdAt, publishedAt, publishAttempts + 1, error);
    }
}

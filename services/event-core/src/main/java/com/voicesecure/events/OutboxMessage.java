package com.voicesecure.events;

import java.time.Instant;
import java.util.Objects;

public record OutboxMessage(EventEnvelope envelope, Instant createdAt, Instant publishedAt) {
    public OutboxMessage {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public OutboxMessage withPublishedAt(Instant newPublishedAt) {
        return new OutboxMessage(envelope, createdAt, Objects.requireNonNull(newPublishedAt, "newPublishedAt"));
    }
}


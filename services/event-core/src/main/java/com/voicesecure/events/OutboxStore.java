package com.voicesecure.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxStore {
    void append(EventEnvelope envelope);

    List<OutboxMessage> pending();

    void markPublished(UUID eventId, Instant publishedAt);

    void markFailed(UUID eventId, String error);

    int size();

    List<OutboxMessage> all();
}

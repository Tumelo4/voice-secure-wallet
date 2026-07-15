package com.voicesecure.events;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DurableOutboxStore {
    List<OutboxMessage> claimPending(UUID workerId, int limit, Instant now, Duration lease);

    void markPublished(UUID eventId, UUID workerId, Instant publishedAt);

    void markFailed(UUID eventId, UUID workerId, Instant failedAt, String error, Duration retryDelay);

    void markDeadLettered(UUID eventId, UUID workerId, Instant failedAt, String reason);
}

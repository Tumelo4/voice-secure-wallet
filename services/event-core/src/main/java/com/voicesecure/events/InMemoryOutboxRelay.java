package com.voicesecure.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class InMemoryOutboxRelay {
    private final OutboxStore store;
    private final EventPublisher publisher;

    public InMemoryOutboxRelay(OutboxStore store, EventPublisher publisher) {
        this.store = Objects.requireNonNull(store, "store");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    public RelayResult relayPending() {
        int published = 0;
        int failed = 0;
        for (OutboxMessage message : store.pending()) {
            try {
                publisher.publish(message.envelope());
                store.markPublished(message.envelope().eventId(), Instant.now());
                published++;
            } catch (RuntimeException ex) {
                store.markFailed(message.envelope().eventId(), ex.getMessage());
                failed++;
            }
        }
        return new RelayResult(published, failed, store.size(), store.pending().size());
    }

    public record RelayResult(int publishedCount, int failedCount, int totalCount, int pendingCount) {
    }
}

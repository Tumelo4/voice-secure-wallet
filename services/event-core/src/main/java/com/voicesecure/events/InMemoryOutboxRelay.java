package com.voicesecure.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class InMemoryOutboxRelay {
    private final InMemoryOutboxStore store;
    private final EventPublisher publisher;

    public InMemoryOutboxRelay(InMemoryOutboxStore store, EventPublisher publisher) {
        this.store = Objects.requireNonNull(store, "store");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    public RelayResult relayPending() {
        int published = 0;
        for (OutboxMessage message : store.pending()) {
            publisher.publish(message.envelope());
            store.markPublished(message.envelope().eventId(), Instant.now());
            published++;
        }
        return new RelayResult(published, store.size(), store.pending().size());
    }

    public record RelayResult(int publishedCount, int totalCount, int pendingCount) {
    }
}


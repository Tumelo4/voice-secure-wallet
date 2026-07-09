package com.voicesecure.events;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryEventPublisher implements EventPublisher {
    private final List<EventEnvelope> published = new ArrayList<>();

    @Override
    public synchronized void publish(EventEnvelope envelope) {
        published.add(envelope);
    }

    public synchronized List<EventEnvelope> published() {
        return List.copyOf(published);
    }
}

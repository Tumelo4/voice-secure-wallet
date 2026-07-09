package com.voicesecure.events;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class InMemoryOutboxStore implements OutboxStore {
    private final Map<UUID, OutboxMessage> messages = new LinkedHashMap<>();

    @Override
    public synchronized void append(EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (messages.containsKey(envelope.eventId())) {
            throw new EventEnvelopeException("duplicate event id: " + envelope.eventId());
        }
        messages.put(envelope.eventId(), new OutboxMessage(envelope, Instant.now(), null));
    }

    @Override
    public synchronized List<OutboxMessage> pending() {
        List<OutboxMessage> pending = new ArrayList<>();
        for (OutboxMessage message : messages.values()) {
            if (message.publishedAt() == null) {
                pending.add(message);
            }
        }
        return List.copyOf(pending);
    }

    @Override
    public synchronized void markPublished(UUID eventId, Instant publishedAt) {
        OutboxMessage existing = messages.get(eventId);
        if (existing == null) {
            throw new EventEnvelopeException("unknown event id: " + eventId);
        }
        messages.put(eventId, existing.withPublishedAt(publishedAt));
    }

    @Override
    public synchronized void markFailed(UUID eventId, String error) {
        OutboxMessage existing = messages.get(eventId);
        if (existing == null) {
            throw new EventEnvelopeException("unknown event id: " + eventId);
        }
        messages.put(eventId, existing.withFailure(error));
    }

    @Override
    public synchronized int size() {
        return messages.size();
    }

    @Override
    public synchronized List<OutboxMessage> all() {
        return List.copyOf(messages.values());
    }
}

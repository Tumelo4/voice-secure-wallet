package com.voicesecure.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class EventEnvelopeFactory {
    private EventEnvelopeFactory() {
    }

    public static EventEnvelope create(
            EventTopic topic,
            UUID aggregateId,
            String aggregateType,
            String eventType,
            Instant occurredAt,
            String traceId,
            String payload
    ) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(payload, "payload");
        return new EventEnvelope(
                UUID.randomUUID(),
                topic.topicName(),
                topic.partitionKeyField(),
                aggregateId.toString(),
                eventType,
                EventEnvelope.DEFAULT_EVENT_VERSION,
                aggregateId,
                aggregateType,
                occurredAt,
                traceId,
                payload
        );
    }
}

package com.voicesecure.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String topic,
        String partitionKeyField,
        String partitionKeyValue,
        String eventType,
        String eventVersion,
        UUID aggregateId,
        String aggregateType,
        Instant occurredAt,
        String traceId,
        String payload
) {
    public static final String DEFAULT_EVENT_VERSION = "1.0";

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(partitionKeyField, "partitionKeyField");
        Objects.requireNonNull(partitionKeyValue, "partitionKeyValue");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(eventVersion, "eventVersion");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(payload, "payload");
        if (topic.isBlank()) {
            throw new EventEnvelopeException("topic cannot be blank");
        }
        if (partitionKeyField.isBlank()) {
            throw new EventEnvelopeException("partition key field cannot be blank");
        }
        if (partitionKeyValue.isBlank()) {
            throw new EventEnvelopeException("partition key value cannot be blank");
        }
        if (eventType.isBlank()) {
            throw new EventEnvelopeException("event type cannot be blank");
        }
        if (eventVersion.isBlank()) {
            throw new EventEnvelopeException("event version cannot be blank");
        }
        if (aggregateType.isBlank()) {
            throw new EventEnvelopeException("aggregate type cannot be blank");
        }
        if (traceId.isBlank()) {
            throw new EventEnvelopeException("trace id cannot be blank");
        }
        if (payload.isBlank()) {
            throw new EventEnvelopeException("payload cannot be blank");
        }
    }

    public boolean isForTopic(EventTopic topicDefinition) {
        return topic.equals(topicDefinition.topicName());
    }
}


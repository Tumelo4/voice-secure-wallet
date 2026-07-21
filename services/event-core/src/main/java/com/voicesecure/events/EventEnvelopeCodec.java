package com.voicesecure.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Stable broker wire codec for domain-event envelopes. */
public final class EventEnvelopeCodec {
    private static final ObjectMapper JSON = new ObjectMapper();

    private EventEnvelopeCodec() { }

    public static String encode(EventEnvelope envelope) {
        return EventEnvelopeJson.toJson(envelope);
    }

    public static EventEnvelope decode(String value) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> fields = JSON.readValue(value, Map.class);
            return new EventEnvelope(
                    UUID.fromString(required(fields, "eventId")),
                    required(fields, "topic"),
                    required(fields, "partitionKeyField"),
                    required(fields, "partitionKeyValue"),
                    required(fields, "eventType"),
                    required(fields, "eventVersion"),
                    UUID.fromString(required(fields, "aggregateId")),
                    required(fields, "aggregateType"),
                    Instant.parse(required(fields, "occurredAt")),
                    required(fields, "traceId"),
                    required(fields, "payload"));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new EventEnvelopeException("invalid event envelope JSON", exception);
        }
    }

    private static String required(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new EventEnvelopeException("missing event envelope field: " + name);
        }
        return text;
    }
}

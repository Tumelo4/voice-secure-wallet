package com.voicesecure.events;

import java.util.Map;
import java.util.Objects;

public record KafkaRecord(
        String topic,
        String key,
        String value,
        Map<String, String> headers
) {
    public KafkaRecord {
        topic = requireText(topic, "topic");
        key = requireText(key, "key");
        value = requireText(value, "value");
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
    }

    private static String requireText(String value, String name) {
        value = Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new EventEnvelopeException(name + " cannot be blank");
        }
        return value;
    }
}

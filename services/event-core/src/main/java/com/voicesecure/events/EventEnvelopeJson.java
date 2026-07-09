package com.voicesecure.events;

import java.util.LinkedHashMap;
import java.util.Map;

final class EventEnvelopeJson {
    private EventEnvelopeJson() {
    }

    static String toJson(EventEnvelope envelope) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventId", envelope.eventId().toString());
        fields.put("topic", envelope.topic());
        fields.put("partitionKeyField", envelope.partitionKeyField());
        fields.put("partitionKeyValue", envelope.partitionKeyValue());
        fields.put("eventType", envelope.eventType());
        fields.put("eventVersion", envelope.eventVersion());
        fields.put("aggregateId", envelope.aggregateId().toString());
        fields.put("aggregateType", envelope.aggregateType());
        fields.put("occurredAt", envelope.occurredAt().toString());
        fields.put("traceId", envelope.traceId());
        fields.put("payload", envelope.payload());

        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append('"')
                    .append(':')
                    .append('"').append(escape(entry.getValue())).append('"');
        }
        return builder.append('}').toString();
    }

    static Map<String, String> headers(EventEnvelope envelope) {
        return Map.of(
                "eventType", envelope.eventType(),
                "eventVersion", envelope.eventVersion(),
                "aggregateType", envelope.aggregateType(),
                "partitionKeyField", envelope.partitionKeyField(),
                "traceId", envelope.traceId()
        );
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}

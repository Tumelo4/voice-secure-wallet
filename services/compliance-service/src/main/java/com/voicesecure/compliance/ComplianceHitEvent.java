package com.voicesecure.compliance;

import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventEnvelopeFactory;
import com.voicesecure.events.EventTopic;
import java.util.Objects;

public record ComplianceHitEvent(
        ComplianceScreeningResult result,
        String traceId
) {
    public ComplianceHitEvent {
        Objects.requireNonNull(result, "result");
        traceId = traceId == null ? "" : traceId.trim();
        if (traceId.isBlank()) {
            throw new ComplianceException("trace id is required for compliance.hit");
        }
        if (!result.hit()) {
            throw new IllegalArgumentException("clear compliance result cannot publish compliance.hit");
        }
    }

    public static ComplianceHitEvent from(ComplianceScreeningResult result, String traceId) {
        return new ComplianceHitEvent(result, traceId);
    }

    public EventEnvelope toEnvelope() {
        return EventEnvelopeFactory.create(
                EventTopic.COMPLIANCE,
                result.userId(),
                "ComplianceScreeningResult",
                "compliance.hit",
                result.screenedAt(),
                traceId,
                payloadJson()
        );
    }

    private String payloadJson() {
        return "{"
                + "\"caseId\":\"" + escape(result.caseId().toString()) + "\","
                + "\"userId\":\"" + escape(result.userId().toString()) + "\","
                + "\"hitType\":\"" + result.hitType().name() + "\","
                + "\"subject\":\"" + escape(result.subject()) + "\","
                + "\"reason\":\"" + escape(result.reason()) + "\""
                + "}";
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

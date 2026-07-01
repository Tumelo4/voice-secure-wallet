package com.voicesecure.fraud;

import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventEnvelopeFactory;
import com.voicesecure.events.EventTopic;
import java.util.Objects;
import java.util.UUID;

public record FraudScoredEvent(
        UUID userId,
        UUID transactionId,
        double score,
        boolean approved,
        AuthPolicy authPolicy,
        double voiceThreshold,
        int velocityCount,
        double deviceTrustScore,
        String reason,
        java.time.Instant occurredAt,
        String traceId
) {
    public FraudScoredEvent {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(authPolicy, "authPolicy");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(occurredAt, "occurredAt");
        traceId = traceId == null ? "" : traceId.trim();
        if (traceId.isBlank()) {
            throw new FraudException("trace id is required for fraud.scored");
        }
    }

    public static FraudScoredEvent from(FraudTransactionRequest request, FraudAssessment assessment, String traceId) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(assessment, "assessment");
        return new FraudScoredEvent(
                request.complianceProfile().userId(),
                request.transactionId(),
                assessment.score(),
                assessment.approved(),
                assessment.authPolicy(),
                assessment.voiceThreshold(),
                assessment.velocityCount(),
                assessment.deviceTrustScore(),
                assessment.reason(),
                request.occurredAt(),
                traceId
        );
    }

    public EventEnvelope toEnvelope() {
        return EventEnvelopeFactory.create(
                EventTopic.FRAUD,
                userId,
                "FraudAssessment",
                "fraud.scored",
                occurredAt,
                traceId,
                payloadJson()
        );
    }

    private String payloadJson() {
        return "{"
                + "\"userId\":\"" + escape(userId.toString()) + "\","
                + "\"transactionId\":\"" + escape(transactionId.toString()) + "\","
                + "\"score\":" + format(score) + ","
                + "\"approved\":" + approved + ","
                + "\"authPolicy\":\"" + authPolicy.name() + "\","
                + "\"voiceThreshold\":" + format(voiceThreshold) + ","
                + "\"velocityCount\":" + velocityCount + ","
                + "\"deviceTrustScore\":" + format(deviceTrustScore) + ","
                + "\"reason\":\"" + escape(reason) + "\""
                + "}";
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

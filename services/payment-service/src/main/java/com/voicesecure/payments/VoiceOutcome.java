package com.voicesecure.payments;

import java.util.Objects;

public record VoiceOutcome(VoiceOutcomeStatus status, double confidence, String reason) {
    public VoiceOutcome {
        Objects.requireNonNull(status, "status");
        reason = reason == null ? "" : reason.trim();
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new PaymentException("voice confidence must be between 0 and 1");
        }
    }
}


package com.voicesecure.payments;

import java.util.Objects;

public record FraudDecision(double score, AuthPolicy authPolicy, boolean approved, String reason) {
    public FraudDecision {
        Objects.requireNonNull(authPolicy, "authPolicy");
        reason = reason == null ? "" : reason.trim();
        if (Double.isNaN(score) || score < 0.0 || score > 1.0) {
            throw new PaymentException("fraud score must be between 0 and 1");
        }
        if (!approved && reason.isEmpty()) {
            reason = "fraud decision rejected";
        }
    }
}

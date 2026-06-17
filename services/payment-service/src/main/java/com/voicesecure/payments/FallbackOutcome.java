package com.voicesecure.payments;

import java.util.Objects;

public record FallbackOutcome(FallbackMethod method, boolean verified, String reason) {
    public FallbackOutcome {
        Objects.requireNonNull(method, "method");
        reason = reason == null ? "" : reason.trim();
        if (verified && reason.isEmpty()) {
            reason = "verified";
        }
    }
}


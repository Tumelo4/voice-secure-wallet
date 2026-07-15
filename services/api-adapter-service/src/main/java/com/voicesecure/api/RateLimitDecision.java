package com.voicesecure.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record RateLimitDecision(boolean allowed, int remaining, Instant resetAt, Duration retryAfter) {
    public RateLimitDecision {
        if (remaining < 0) {
            throw new IllegalArgumentException("remaining capacity cannot be negative");
        }
        Objects.requireNonNull(resetAt, "resetAt");
        Objects.requireNonNull(retryAfter, "retryAfter");
        if (retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter cannot be negative");
        }
        if (allowed && !retryAfter.isZero()) {
            throw new IllegalArgumentException("allowed decisions cannot require a retry");
        }
    }

    public long retryAfterSeconds() {
        if (retryAfter.isZero()) {
            return 0;
        }
        long seconds = retryAfter.toSeconds();
        return retryAfter.minusSeconds(seconds).isZero() ? Math.max(1, seconds) : seconds + 1;
    }
}

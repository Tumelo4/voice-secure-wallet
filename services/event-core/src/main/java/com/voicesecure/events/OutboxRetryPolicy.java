package com.voicesecure.events;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public record OutboxRetryPolicy(Duration baseDelay, Duration maxDelay, int maxAttempts, double jitterFraction) {
    public OutboxRetryPolicy {
        requirePositive(baseDelay, "baseDelay");
        requirePositive(maxDelay, "maxDelay");
        if (maxDelay.compareTo(baseDelay) < 0) throw new IllegalArgumentException("maxDelay must be at least baseDelay");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        if (jitterFraction < 0.0 || jitterFraction > 1.0) throw new IllegalArgumentException("jitterFraction must be between 0 and 1");
    }

    public Duration delayFor(UUID eventId, int attempt) {
        Objects.requireNonNull(eventId, "eventId");
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
        long multiplier = 1L << Math.min(30, attempt - 1);
        long exponential;
        try {
            exponential = Math.multiplyExact(baseDelay.toMillis(), multiplier);
        } catch (ArithmeticException ignored) {
            exponential = maxDelay.toMillis();
        }
        long capped = Math.min(exponential, maxDelay.toMillis());
        long jitterRange = Math.round(capped * jitterFraction);
        if (jitterRange == 0) return Duration.ofMillis(capped);
        long spread = (jitterRange * 2L) + 1L;
        long offset = Math.floorMod(eventId.getLeastSignificantBits(), spread) - jitterRange;
        return Duration.ofMillis(Math.max(1, capped + offset));
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
    }
}

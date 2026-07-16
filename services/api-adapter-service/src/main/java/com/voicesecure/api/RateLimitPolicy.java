package com.voicesecure.api;

import java.time.Duration;

public record RateLimitPolicy(int capacity, Duration window, boolean failClosed) {
    public RateLimitPolicy {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        if (window == null || window.isZero() || window.isNegative()) throw new IllegalArgumentException("window must be positive");
    }

    public static RateLimitPolicy forRisk(RateLimitRisk risk) {
        return switch (risk) {
            case HIGH -> new RateLimitPolicy(10, Duration.ofMinutes(1), true);
            case MEDIUM -> new RateLimitPolicy(60, Duration.ofMinutes(1), true);
            case LOW -> new RateLimitPolicy(300, Duration.ofMinutes(1), false);
        };
    }
}

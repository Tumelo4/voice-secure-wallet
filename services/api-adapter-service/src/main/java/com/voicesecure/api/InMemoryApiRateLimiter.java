package com.voicesecure.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class InMemoryApiRateLimiter implements ApiRateLimiter {
    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    public InMemoryApiRateLimiter(int maxRequests) {
        this(maxRequests, Duration.ofMinutes(1), Clock.systemUTC());
    }

    public InMemoryApiRateLimiter(int maxRequests, Duration window, Clock clock) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("max requests must be positive");
        }
        Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized RateLimitDecision evaluate(RateLimitContext context) {
        Objects.requireNonNull(context, "context");
        String principalId = context.key();
        if (principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("principalId is required");
        }
        Instant now = clock.instant();
        Window current = windows.get(principalId);
        if (current == null || !now.isBefore(current.resetAt())) {
            current = new Window(0, now.plus(window));
        }
        if (current.count() >= maxRequests) {
            windows.put(principalId, current);
            return new RateLimitDecision(false, 0, current.resetAt(), Duration.between(now, current.resetAt()));
        }
        Window updated = new Window(current.count() + 1, current.resetAt());
        windows.put(principalId, updated);
        return new RateLimitDecision(true, maxRequests - updated.count(), updated.resetAt(), Duration.ZERO);
    }

    private record Window(int count, Instant resetAt) {}
}

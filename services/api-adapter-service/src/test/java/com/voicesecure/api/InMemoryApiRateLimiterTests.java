package com.voicesecure.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class InMemoryApiRateLimiterTests {
    public static void main(String[] args) {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T12:00:00Z"));
        InMemoryApiRateLimiter limiter = new InMemoryApiRateLimiter(2, Duration.ofSeconds(30), clock);

        RateLimitDecision first = limiter.evaluate("customer-1");
        RateLimitDecision second = limiter.evaluate("customer-1");
        RateLimitDecision rejected = limiter.evaluate("customer-1");

        assertTrue(first.allowed(), "first request should pass");
        assertEquals(1, first.remaining(), "first remaining capacity");
        assertTrue(second.allowed(), "second request should pass");
        assertEquals(0, second.remaining(), "second remaining capacity");
        assertTrue(!rejected.allowed(), "burst overflow should be rejected");
        assertEquals(30L, rejected.retryAfterSeconds(), "retry derives from reset time");

        clock.advance(Duration.ofSeconds(30));
        RateLimitDecision reset = limiter.evaluate("customer-1");
        assertTrue(reset.allowed(), "capacity should reset after the window");
        assertEquals(1, reset.remaining(), "reset remaining capacity");

        RateLimitDecision otherPrincipal = limiter.evaluate("customer-2");
        assertTrue(otherPrincipal.allowed(), "principals have independent local windows");
        System.out.println("In-memory API rate limiter tests passed: 7");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + " but got " + actual);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}

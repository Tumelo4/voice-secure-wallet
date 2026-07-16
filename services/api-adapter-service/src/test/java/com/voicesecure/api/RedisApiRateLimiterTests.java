package com.voicesecure.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class RedisApiRateLimiterTests {
    public static void main(String[] args) {
        Clock clock = Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"), ZoneOffset.UTC);
        AtomicInteger calls = new AtomicInteger();
        RedisApiRateLimiter limiter = new RedisApiRateLimiter((script, key, capacity, window) -> {
            int count = calls.incrementAndGet();
            return List.of(count <= capacity ? 1L : 0L, Math.max(0L, capacity - count), window);
        }, clock, RateLimitTelemetry.NOOP);
        RateLimitContext high = new RateLimitContext("test", "customer", "POST", "/payments", RateLimitRisk.HIGH);
        for (int index = 0; index < 10; index++) assertTrue(limiter.evaluate(high).allowed(), "high-risk capacity");
        RateLimitDecision rejected = limiter.evaluate(high);
        assertTrue(!rejected.allowed(), "shared capacity is bounded");
        assertEquals(60L, rejected.retryAfterSeconds(), "retry derives from Redis TTL");

        RedisApiRateLimiter unavailable = new RedisApiRateLimiter((a, b, c, d) -> {
            throw new IllegalStateException("redis unavailable");
        }, clock, RateLimitTelemetry.NOOP);
        assertTrue(!unavailable.evaluate(high).allowed(), "high-risk mutation fails closed");
        RateLimitContext low = new RateLimitContext("test", "customer", "GET", "/wallets", RateLimitRisk.LOW);
        assertTrue(unavailable.evaluate(low).allowed(), "low-risk read degrades open");
        System.out.println("Redis API rate limiter tests passed: 14");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + " but got " + actual);
    }
}

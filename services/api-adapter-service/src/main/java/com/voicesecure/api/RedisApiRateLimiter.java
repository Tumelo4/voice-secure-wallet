package com.voicesecure.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class RedisApiRateLimiter implements ApiRateLimiter {
    static final String SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[2]); ttl = tonumber(ARGV[2]) end
            local allowed = count <= tonumber(ARGV[1]) and 1 or 0
            local remaining = math.max(0, tonumber(ARGV[1]) - count)
            return {allowed, remaining, ttl}
            """;

    @FunctionalInterface
    public interface ScriptExecutor {
        List<Long> execute(String script, String key, int capacity, long windowMillis);
    }

    private final ScriptExecutor redis;
    private final Clock clock;
    private final RateLimitTelemetry telemetry;

    public RedisApiRateLimiter(ScriptExecutor redis, Clock clock, RateLimitTelemetry telemetry) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
    }

    @Override
    public RateLimitDecision evaluate(RateLimitContext context) {
        Objects.requireNonNull(context, "context");
        RateLimitPolicy policy = RateLimitPolicy.forRisk(context.risk());
        Instant started = clock.instant();
        try {
            List<Long> result = redis.execute(SCRIPT, redisKey(context), policy.capacity(), policy.window().toMillis());
            if (result.size() != 3) throw new IllegalStateException("unexpected Redis rate-limit result");
            boolean allowed = result.get(0) == 1L;
            Duration ttl = Duration.ofMillis(Math.max(1, result.get(2)));
            RateLimitDecision decision = new RateLimitDecision(
                    allowed, Math.toIntExact(result.get(1)), started.plus(ttl), allowed ? Duration.ZERO : ttl);
            telemetry.decision(allowed, context.risk(), Duration.between(started, clock.instant()));
            return decision;
        } catch (RuntimeException failure) {
            telemetry.redisFailure(context.risk(), policy.failClosed());
            Duration retry = policy.failClosed() ? Duration.ofSeconds(1) : Duration.ZERO;
            return new RateLimitDecision(!policy.failClosed(), 0, started.plusSeconds(1), retry);
        }
    }

    private static String redisKey(RateLimitContext context) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(context.key().getBytes(StandardCharsets.UTF_8));
            return "vsw:rate-limit:" + java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}

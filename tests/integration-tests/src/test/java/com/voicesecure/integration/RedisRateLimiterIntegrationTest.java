package com.voicesecure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.voicesecure.api.JedisRateLimitScriptExecutor;
import com.voicesecure.api.RateLimitContext;
import com.voicesecure.api.RateLimitRisk;
import com.voicesecure.api.RateLimitTelemetry;
import com.voicesecure.api.RedisApiRateLimiter;
import java.net.URI;
import java.time.Clock;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
final class RedisRateLimiterIntegrationTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4.2-alpine"))
            .withExposedPorts(6379);

    @Test
    void twoApiInstancesEnforceOneCombinedHighRiskLimit() {
        URI uri = URI.create("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        RateLimitContext request = new RateLimitContext(
                "integration", "customer-42", "POST", "/v1/payments", RateLimitRisk.HIGH);
        try (JedisRateLimitScriptExecutor firstRedis = new JedisRateLimitScriptExecutor(uri);
             JedisRateLimitScriptExecutor secondRedis = new JedisRateLimitScriptExecutor(uri)) {
            RedisApiRateLimiter first = new RedisApiRateLimiter(firstRedis, Clock.systemUTC(), RateLimitTelemetry.NOOP);
            RedisApiRateLimiter second = new RedisApiRateLimiter(secondRedis, Clock.systemUTC(), RateLimitTelemetry.NOOP);
            long allowed = IntStream.range(0, 12)
                    .mapToObj(index -> (index & 1) == 0 ? first : second)
                    .filter(limiter -> limiter.evaluate(request).allowed())
                    .count();
            assertEquals(10, allowed, "both instances must share the ten-request high-risk budget");
        }
    }
}

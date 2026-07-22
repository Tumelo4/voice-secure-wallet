package com.voicesecure.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintStream;
import java.time.Duration;
import java.util.Objects;

public final class StructuredRateLimitTelemetry implements RateLimitTelemetry {
    private final PrintStream output;
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "PrintStream is an injected process-owned telemetry sink")
    public StructuredRateLimitTelemetry(PrintStream output) { this.output = Objects.requireNonNull(output); }
    @Override public void decision(boolean allowed, RateLimitRisk risk, Duration latency) {
        output.println("{\"type\":\"rate_limit_decision\",\"allowed\":" + allowed
                + ",\"risk\":" + ApiJson.quote(risk.name()) + ",\"latencyMicros\":" + latency.toNanos() / 1000 + "}");
    }
    @Override public void redisFailure(RateLimitRisk risk, boolean failedClosed) {
        output.println("{\"type\":\"rate_limit_redis_failure\",\"risk\":" + ApiJson.quote(risk.name())
                + ",\"failedClosed\":" + failedClosed + "}");
    }
}

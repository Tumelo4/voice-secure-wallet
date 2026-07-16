package com.voicesecure.api;

import java.time.Duration;

public interface RateLimitTelemetry {
    void decision(boolean allowed, RateLimitRisk risk, Duration latency);
    void redisFailure(RateLimitRisk risk, boolean failedClosed);

    RateLimitTelemetry NOOP = new RateLimitTelemetry() {
        public void decision(boolean allowed, RateLimitRisk risk, Duration latency) { }
        public void redisFailure(RateLimitRisk risk, boolean failedClosed) { }
    };
}

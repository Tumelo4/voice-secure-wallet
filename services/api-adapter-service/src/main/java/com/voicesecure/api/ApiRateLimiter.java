package com.voicesecure.api;

public interface ApiRateLimiter {
    RateLimitDecision evaluate(RateLimitContext context);

    default RateLimitDecision evaluate(String principalId) {
        return evaluate(new RateLimitContext("local", principalId, "UNKNOWN", "/", RateLimitRisk.LOW));
    }
}

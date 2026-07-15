package com.voicesecure.api;

public interface ApiRateLimiter {
    RateLimitDecision evaluate(String principalId);
}

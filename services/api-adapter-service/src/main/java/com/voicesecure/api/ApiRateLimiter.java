package com.voicesecure.api;

public interface ApiRateLimiter {
    boolean allow(String principalId);
}

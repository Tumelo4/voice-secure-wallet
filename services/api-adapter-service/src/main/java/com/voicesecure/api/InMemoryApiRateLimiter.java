package com.voicesecure.api;

import java.util.HashMap;
import java.util.Map;

public final class InMemoryApiRateLimiter implements ApiRateLimiter {
    private final int maxRequests;
    private final Map<String, Integer> counts = new HashMap<>();

    public InMemoryApiRateLimiter(int maxRequests) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("max requests must be positive");
        }
        this.maxRequests = maxRequests;
    }

    @Override
    public synchronized boolean allow(String principalId) {
        int nextCount = counts.getOrDefault(principalId, 0) + 1;
        counts.put(principalId, nextCount);
        return nextCount <= maxRequests;
    }
}

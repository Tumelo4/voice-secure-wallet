package com.voicesecure.api;

import java.util.Objects;

public record RateLimitContext(String environment, String principalId, String method, String route, RateLimitRisk risk) {
    public RateLimitContext {
        if (environment == null || environment.isBlank()) throw new IllegalArgumentException("environment is required");
        if (principalId == null || principalId.isBlank()) throw new IllegalArgumentException("principalId is required");
        if (method == null || method.isBlank()) throw new IllegalArgumentException("method is required");
        if (route == null || route.isBlank()) throw new IllegalArgumentException("route is required");
        Objects.requireNonNull(risk, "risk");
    }

    public String key() {
        return environment + ":" + principalId + ":" + method.toUpperCase() + ":" + route + ":" + risk;
    }
}

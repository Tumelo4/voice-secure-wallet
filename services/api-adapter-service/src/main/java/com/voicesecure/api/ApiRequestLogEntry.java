package com.voicesecure.api;

import java.time.Instant;
import java.util.Objects;

public record ApiRequestLogEntry(
        Instant occurredAt,
        String principalId,
        String traceId,
        String method,
        String path,
        int status
) {
    public ApiRequestLogEntry {
        Objects.requireNonNull(occurredAt, "occurredAt");
        principalId = clean(principalId, "anonymous");
        traceId = clean(traceId, "");
        method = clean(method, "");
        path = clean(path, "");
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status must be a valid HTTP status code");
        }
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}

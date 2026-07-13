package com.voicesecure.api;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record ApiRequest(String method, String path, Map<String, String> headers, String body) {
    public ApiRequest {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        method = method.trim().toUpperCase(Locale.ROOT);
        path = path.trim();
        if (method.isEmpty()) {
            throw new IllegalArgumentException("method is required");
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with /");
        }
        headers = normalizeHeaders(headers);
        body = body == null ? "" : body;
    }

    public String header(String name) {
        Objects.requireNonNull(name, "name");
        return headers.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public ApiRequest withHeader(String name, String value) {
        Objects.requireNonNull(name, "name");
        Map<String, String> nextHeaders = new LinkedHashMap<>(headers);
        nextHeaders.put(name, value == null ? "" : value);
        return new ApiRequest(method, path, nextHeaders, body);
    }

    private static Map<String, String> normalizeHeaders(Map<String, String> headers) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (headers == null) {
            return Map.of();
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) {
                normalized.put(name, entry.getValue() == null ? "" : entry.getValue().trim());
            }
        }
        return Map.copyOf(normalized);
    }
}

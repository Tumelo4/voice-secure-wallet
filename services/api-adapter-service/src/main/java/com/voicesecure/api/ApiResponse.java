package com.voicesecure.api;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record ApiResponse(int status, Map<String, String> headers, String body) {
    public ApiResponse {
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status must be a valid HTTP status code");
        }
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
        body = body == null ? "" : body;
    }

    public static ApiResponse json(int status, String body) {
        return new ApiResponse(status, Map.of("Content-Type", "application/json"), body);
    }

    public static ApiResponse error(int status, String code, String message) {
        String body = "{"
                + "\"code\":" + ApiJson.quote(code) + ","
                + "\"message\":" + ApiJson.quote(message)
                + "}";
        return json(status, body);
    }

    public ApiResponse withHeader(String name, String value) {
        TreeMap<String, String> nextHeaders = new TreeMap<>(headers);
        nextHeaders.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
        return new ApiResponse(status, nextHeaders, body);
    }
}

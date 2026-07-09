package com.voicesecure.api;

import java.util.Objects;
import java.util.Set;

public final class HealthApiAdapter implements ApiEndpoint {
    @Override
    public boolean supports(ApiRequest request) {
        return "GET".equals(request.method()) && isHealthPath(request.path());
    }

    @Override
    public ApiResponse handle(ApiRequest request) {
        if (!supports(request)) {
            return ApiResponse.error(404, "ROUTE_NOT_FOUND", "route not found");
        }
        return ApiResponse.json(200, healthBody(request.path()));
    }

    @Override
    public boolean isPublic(ApiRequest request) {
        return supports(request);
    }

    @Override
    public Set<String> requiredScopes(ApiRequest request) {
        return Set.of();
    }

    private static boolean isHealthPath(String path) {
        Objects.requireNonNull(path, "path");
        return "/health/live".equals(path) || "/health/ready".equals(path);
    }

    private static String healthBody(String path) {
        return "{\"status\":" + ApiJson.quote("/health/live".equals(path) ? "LIVE" : "READY") + "}";
    }
}

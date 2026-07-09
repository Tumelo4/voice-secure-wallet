package com.voicesecure.api;

import java.util.Set;

public interface ApiEndpoint {
    boolean supports(ApiRequest request);

    ApiResponse handle(ApiRequest request);

    default Set<String> requiredScopes(ApiRequest request) {
        return Set.of();
    }

    default boolean isPublic(ApiRequest request) {
        return false;
    }
}

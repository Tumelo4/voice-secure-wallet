package com.voicesecure.api;

public interface ApiEndpoint {
    boolean supports(ApiRequest request);

    ApiResponse handle(ApiRequest request);
}

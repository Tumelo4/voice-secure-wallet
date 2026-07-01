package com.voicesecure.api;

import java.util.Objects;

final class CountingApiEndpoint implements ApiEndpoint {
    private final ApiEndpoint delegate;
    private int invocationCount;

    CountingApiEndpoint(ApiEndpoint delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public boolean supports(ApiRequest request) {
        return delegate.supports(request);
    }

    @Override
    public ApiResponse handle(ApiRequest request) {
        invocationCount++;
        return delegate.handle(request);
    }

    int invocationCount() {
        return invocationCount;
    }
}

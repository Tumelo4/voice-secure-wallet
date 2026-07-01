package com.voicesecure.api;

import java.util.List;
import java.util.Objects;

public final class ApiRouter implements ApiEndpoint {
    private final List<ApiEndpoint> endpoints;

    public ApiRouter(PaymentApiAdapter paymentApiAdapter, WalletApiAdapter walletApiAdapter) {
        this(List.of(paymentApiAdapter, walletApiAdapter));
    }

    public ApiRouter(List<ApiEndpoint> endpoints) {
        Objects.requireNonNull(endpoints, "endpoints");
        this.endpoints = List.copyOf(endpoints);
        if (this.endpoints.isEmpty()) {
            throw new IllegalArgumentException("at least one endpoint is required");
        }
    }

    @Override
    public boolean supports(ApiRequest request) {
        return true;
    }

    @Override
    public ApiResponse handle(ApiRequest request) {
        Objects.requireNonNull(request, "request");
        for (ApiEndpoint endpoint : endpoints) {
            if (endpoint.supports(request)) {
                return endpoint.handle(request);
            }
        }
        return ApiResponse.error(404, "ROUTE_NOT_FOUND", "route not found");
    }
}

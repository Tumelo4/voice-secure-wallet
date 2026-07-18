package com.voicesecure.api;

import com.voicesecure.events.OutboxRelayTelemetry;
import com.voicesecure.events.PrometheusOutboxMetrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class RelayMetricsApiAdapter implements ApiEndpoint {
    private final Map<String, Supplier<OutboxRelayTelemetry.Snapshot>> relays;

    public RelayMetricsApiAdapter(Map<String, Supplier<OutboxRelayTelemetry.Snapshot>> relays) {
        this.relays = Map.copyOf(Objects.requireNonNull(relays, "relays"));
        if (this.relays.isEmpty()) throw new IllegalArgumentException("at least one relay metric is required");
    }

    @Override public boolean supports(ApiRequest request) {
        return "GET".equals(request.method()) && "/internal/metrics".equals(request.path());
    }

    @Override public ApiResponse handle(ApiRequest request) {
        if (!supports(request)) return ApiResponse.error(404, "ROUTE_NOT_FOUND", "route not found");
        Map<String, OutboxRelayTelemetry.Snapshot> snapshots = new LinkedHashMap<>();
        relays.forEach((name, source) -> snapshots.put(name, source.get()));
        return new ApiResponse(200, Map.of("Content-Type", "text/plain; version=0.0.4; charset=utf-8"),
                PrometheusOutboxMetrics.render(snapshots));
    }

    @Override public Set<String> requiredScopes(ApiRequest request) {
        return supports(request) ? Set.of("ops:read") : Set.of();
    }
}

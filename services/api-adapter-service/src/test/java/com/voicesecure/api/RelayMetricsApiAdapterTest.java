package com.voicesecure.api;

import com.voicesecure.events.OutboxRelayTelemetry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RelayMetricsApiAdapterTest {
    @Test
    void exportsStablePrometheusMetricsAndRequiresOperationsScope() {
        OutboxRelayTelemetry.Snapshot snapshot = new OutboxRelayTelemetry.Snapshot(
                4, 1, 12, 10, 1, 1, 1_500_000_000,
                Instant.parse("2026-07-18T18:00:00Z"), Instant.parse("2026-07-18T18:01:00Z"), "SQLException");
        RelayMetricsApiAdapter adapter = new RelayMetricsApiAdapter(Map.of("payments", () -> snapshot));
        ApiRequest request = new ApiRequest("GET", "/internal/metrics", Map.of(), "");

        ApiResponse response = adapter.handle(request);

        assertEquals(200, response.status());
        assertEquals(Set.of("ops:read"), adapter.requiredScopes(request));
        assertTrue(response.headers().get("Content-Type").startsWith("text/plain"));
        assertTrue(response.body().contains("voicesecure_outbox_runs_total{relay=\"payments\"} 4"));
        assertTrue(response.body().contains("voicesecure_outbox_run_duration_seconds_total{relay=\"payments\"} 1.5"));
        assertTrue(response.body().contains("voicesecure_outbox_dead_lettered_total{relay=\"payments\"} 1"));
    }
}

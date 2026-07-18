package com.voicesecure.events;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Renders relay snapshots in the Prometheus text exposition format. */
public final class PrometheusOutboxMetrics {
    private PrometheusOutboxMetrics() { }

    public static String render(Map<String, OutboxRelayTelemetry.Snapshot> relays) {
        Objects.requireNonNull(relays, "relays");
        StringBuilder output = new StringBuilder();
        output.append("# HELP voicesecure_outbox_runs_total Relay executions.\n")
                .append("# TYPE voicesecure_outbox_runs_total counter\n");
        for (var entry : new TreeMap<>(relays).entrySet()) {
            String relay = label(entry.getKey());
            OutboxRelayTelemetry.Snapshot value = Objects.requireNonNull(entry.getValue(), "snapshot");
            metric(output, "voicesecure_outbox_runs_total", relay, value.runs());
            metric(output, "voicesecure_outbox_run_failures_total", relay, value.runFailures());
            metric(output, "voicesecure_outbox_claimed_total", relay, value.claimed());
            metric(output, "voicesecure_outbox_published_total", relay, value.published());
            metric(output, "voicesecure_outbox_retries_total", relay, value.retries());
            metric(output, "voicesecure_outbox_dead_lettered_total", relay, value.deadLettered());
            metric(output, "voicesecure_outbox_run_duration_seconds_total", relay,
                    value.totalDurationNanos() / 1_000_000_000.0);
            metric(output, "voicesecure_outbox_last_success_timestamp_seconds", relay, epoch(value.lastSuccessAt()));
            metric(output, "voicesecure_outbox_last_failure_timestamp_seconds", relay, epoch(value.lastFailureAt()));
        }
        return output.toString();
    }

    private static void metric(StringBuilder output, String name, String relay, Number value) {
        output.append(name).append("{relay=\"").append(relay).append("\"} ").append(value).append('\n');
    }

    private static long epoch(Instant value) {
        return value == null ? 0 : value.getEpochSecond();
    }

    private static String label(String value) {
        Objects.requireNonNull(value, "relay name");
        if (!value.matches("[a-z][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("relay name must be a bounded metric label");
        }
        return value;
    }
}

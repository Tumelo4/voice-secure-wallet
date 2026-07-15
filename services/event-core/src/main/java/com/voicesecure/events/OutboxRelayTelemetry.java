package com.voicesecure.events;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe relay measurements that production metrics adapters can scrape or export. */
public final class OutboxRelayTelemetry {
    private final AtomicLong runs = new AtomicLong();
    private final AtomicLong runFailures = new AtomicLong();
    private final AtomicLong claimed = new AtomicLong();
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong deadLettered = new AtomicLong();
    private final AtomicLong totalDurationNanos = new AtomicLong();
    private final AtomicReference<Instant> lastSuccessAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastFailureAt = new AtomicReference<>();
    private final AtomicReference<String> lastFailure = new AtomicReference<>();

    void recordCompletion(TransactionalOutboxRelay.RelayResult result, Duration duration, Instant completedAt) {
        Objects.requireNonNull(result, "result");
        runs.incrementAndGet();
        claimed.addAndGet(result.claimedCount());
        published.addAndGet(result.publishedCount());
        retries.addAndGet(result.failedCount());
        deadLettered.addAndGet(result.deadLetteredCount());
        totalDurationNanos.addAndGet(duration.toNanos());
        lastSuccessAt.set(completedAt);
    }

    void recordRunFailure(RuntimeException failure, Duration duration, Instant failedAt) {
        runs.incrementAndGet();
        runFailures.incrementAndGet();
        totalDurationNanos.addAndGet(duration.toNanos());
        lastFailureAt.set(failedAt);
        lastFailure.set(failure.getClass().getSimpleName());
    }

    public Snapshot snapshot() {
        return new Snapshot(runs.get(), runFailures.get(), claimed.get(), published.get(), retries.get(),
                deadLettered.get(), totalDurationNanos.get(), lastSuccessAt.get(), lastFailureAt.get(), lastFailure.get());
    }

    public record Snapshot(long runs, long runFailures, long claimed, long published, long retries,
                           long deadLettered, long totalDurationNanos, Instant lastSuccessAt,
                           Instant lastFailureAt, String lastFailure) { }
}

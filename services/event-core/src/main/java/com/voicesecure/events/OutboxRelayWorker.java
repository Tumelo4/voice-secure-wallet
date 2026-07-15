package com.voicesecure.events;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class OutboxRelayWorker implements AutoCloseable {
    private final ScheduledExecutorService executor;

    public OutboxRelayWorker(String threadName, TransactionalOutboxRelay relay, Duration interval,
                             Consumer<RuntimeException> failureHandler) {
        Objects.requireNonNull(threadName, "threadName");
        Objects.requireNonNull(relay, "relay");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(failureHandler, "failureHandler");
        if (interval.isZero() || interval.isNegative()) throw new IllegalArgumentException("interval must be positive");
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, threadName));
        executor.scheduleWithFixedDelay(() -> {
            try { relay.relayOnce(); } catch (RuntimeException exception) { failureHandler.accept(exception); }
        }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}

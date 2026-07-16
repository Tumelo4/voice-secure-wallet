package com.voicesecure.payments;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class PaymentRecoveryWorker implements AutoCloseable {
    private final ScheduledExecutorService executor;

    public PaymentRecoveryWorker(PaymentRecoveryCoordinator coordinator, Duration interval,
                                 Consumer<RuntimeException> failureHandler) {
        Objects.requireNonNull(coordinator, "coordinator");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(failureHandler, "failureHandler");
        if (interval.isZero() || interval.isNegative()) throw new IllegalArgumentException("interval must be positive");
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "payment-recovery");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try { coordinator.runOnce(); } catch (RuntimeException failure) { failureHandler.accept(failure); }
        }, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override public void close() { executor.shutdownNow(); }
}

package com.voicesecure.events;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class TransactionalOutboxRelay {
    private final DurableOutboxStore store;
    private final EventPublisher publisher;
    private final UUID workerId;
    private final Clock clock;
    private final Duration lease;
    private final OutboxRetryPolicy retryPolicy;
    private final int batchSize;

    public TransactionalOutboxRelay(DurableOutboxStore store, EventPublisher publisher, UUID workerId,
                                    Clock clock, Duration lease, Duration retryDelay, int batchSize) {
        this(store, publisher, workerId, clock, lease,
                new OutboxRetryPolicy(retryDelay, Duration.ofMinutes(5), 8, 0.2), batchSize);
    }

    public TransactionalOutboxRelay(DurableOutboxStore store, EventPublisher publisher, UUID workerId,
                                    Clock clock, Duration lease, OutboxRetryPolicy retryPolicy, int batchSize) {
        this.store = Objects.requireNonNull(store, "store");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lease = requirePositive(lease, "lease");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        if (batchSize < 1 || batchSize > 1_000) throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        this.batchSize = batchSize;
    }

    public RelayResult relayOnce() {
        Instant now = clock.instant();
        List<OutboxMessage> claimed = store.claimPending(workerId, batchSize, now, lease);
        int published = 0;
        int failed = 0;
        int deadLettered = 0;
        for (OutboxMessage message : claimed) {
            try {
                publisher.publish(message.envelope());
                store.markPublished(message.envelope().eventId(), workerId, clock.instant());
                published++;
            } catch (RuntimeException exception) {
                String error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                boolean permanent = exception instanceof PermanentEventPublicationException
                        || message.publishAttempts() >= retryPolicy.maxAttempts();
                if (permanent) {
                    store.markDeadLettered(message.envelope().eventId(), workerId, clock.instant(), error);
                    deadLettered++;
                } else {
                    store.markFailed(message.envelope().eventId(), workerId, clock.instant(), error,
                            retryPolicy.delayFor(message.envelope().eventId(), message.publishAttempts()));
                    failed++;
                }
            }
        }
        return new RelayResult(claimed.size(), published, failed, deadLettered);
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    public record RelayResult(int claimedCount, int publishedCount, int failedCount, int deadLetteredCount) { }
}

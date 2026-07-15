package com.voicesecure.events;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public final class TransactionalOutboxRelayTests {
    private static final Instant NOW = Instant.parse("2026-07-15T12:00:00Z");

    public static void main(String[] args) {
        retriesWithExponentialBackoff();
        deadLettersAfterBoundedAttempts();
        deadLettersPermanentFailuresImmediately();
        System.out.println("Transactional outbox relay tests passed: 3");
    }

    private static void retriesWithExponentialBackoff() {
        RecordingStore store = new RecordingStore(message(3));
        TransactionalOutboxRelay.RelayResult result = relay(store,
                ignored -> { throw new EventEnvelopeException("broker down"); }).relayOnce();
        assertEquals(1, result.failedCount(), "retry count");
        assertEquals(0, result.deadLetteredCount(), "dead-letter count");
        assertEquals(Duration.ofSeconds(20), store.retryDelay, "third attempt backoff");
    }

    private static void deadLettersAfterBoundedAttempts() {
        RecordingStore store = new RecordingStore(message(4));
        TransactionalOutboxRelay.RelayResult result = relay(store,
                ignored -> { throw new EventEnvelopeException("still down"); }).relayOnce();
        assertEquals(0, result.failedCount(), "bounded retry count");
        assertEquals(1, result.deadLetteredCount(), "bounded dead-letter count");
        assertEquals("still down", store.deadLetterReason, "persisted poison reason");
    }

    private static void deadLettersPermanentFailuresImmediately() {
        RecordingStore store = new RecordingStore(message(1));
        TransactionalOutboxRelay.RelayResult result = relay(store,
                ignored -> { throw new PermanentEventPublicationException("invalid payload"); }).relayOnce();
        assertEquals(1, result.deadLetteredCount(), "permanent dead-letter count");
        assertEquals("invalid payload", store.deadLetterReason, "permanent failure reason");
    }

    private static TransactionalOutboxRelay relay(RecordingStore store, EventPublisher publisher) {
        return new TransactionalOutboxRelay(store, publisher, store.workerId, Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30), new OutboxRetryPolicy(Duration.ofSeconds(5), Duration.ofMinutes(1), 4, 0.0), 10);
    }

    private static OutboxMessage message(int attempts) {
        UUID aggregateId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), "payments", "sagaId", aggregateId.toString(),
                "payment.completed", "1", aggregateId, "Payment", NOW.minusSeconds(10), "trace-outbox", "{}");
        return new OutboxMessage(envelope, NOW.minusSeconds(10), null, attempts, "");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + " but got " + actual);
    }

    private static final class RecordingStore implements DurableOutboxStore {
        private final UUID workerId = UUID.randomUUID();
        private final OutboxMessage message;
        private Duration retryDelay;
        private String deadLetterReason;
        private RecordingStore(OutboxMessage message) { this.message = message; }
        @Override public List<OutboxMessage> claimPending(UUID workerId, int limit, Instant now, Duration lease) {
            if (!this.workerId.equals(workerId)) throw new AssertionError("unexpected worker");
            return List.of(message);
        }
        @Override public void markPublished(UUID eventId, UUID workerId, Instant publishedAt) {
            throw new AssertionError("failure test must not publish");
        }
        @Override public void markFailed(UUID eventId, UUID workerId, Instant failedAt, String error, Duration retryDelay) {
            this.retryDelay = retryDelay;
        }
        @Override public void markDeadLettered(UUID eventId, UUID workerId, Instant failedAt, String reason) {
            this.deadLetterReason = reason;
        }
    }
}

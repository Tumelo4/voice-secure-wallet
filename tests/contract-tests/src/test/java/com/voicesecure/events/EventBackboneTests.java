package com.voicesecure.events;

import com.voicesecure.ledger.LedgerEntry;
import com.voicesecure.ledger.EntryType;
import com.voicesecure.payments.PaymentEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EventBackboneTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("payment events map to the payments topic envelope", EventBackboneTests::paymentEventsMapToEnvelope),
                new TestCase("ledger entries map to the ledger topic envelope", EventBackboneTests::ledgerEntriesMapToEnvelope),
                new TestCase("outbox relay publishes pending events in order", EventBackboneTests::outboxRelayPublishesInOrder),
                new TestCase("outbox relay records publish failures for retry", EventBackboneTests::outboxRelayRecordsPublishFailures),
                new TestCase("in-memory publisher records published envelopes", EventBackboneTests::inMemoryPublisherRecordsPublishedEnvelopes),
                new TestCase("topic catalog exposes the plan topics", EventBackboneTests::topicCatalogExposesPlanTopics)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Event backbone tests passed: " + tests.length);
    }

    private static void paymentEventsMapToEnvelope() {
        PaymentEvent paymentEvent = new PaymentEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "payment.completed",
                Instant.parse("2026-06-18T10:15:00Z"),
                "trace-abc",
                "{\"status\":\"complete\"}"
        );

        EventEnvelope envelope = paymentEvent.toEnvelope();

        assertEquals(EventTopic.PAYMENTS.topicName(), envelope.topic(), "payment topic");
        assertEquals(paymentEvent.eventId(), envelope.eventId(), "stable payment event id");
        assertEquals(EventTopic.PAYMENTS.partitionKeyField(), envelope.partitionKeyField(), "payment partition field");
        assertEquals(paymentEvent.sagaId().toString(), envelope.partitionKeyValue(), "payment partition key");
        assertEquals(paymentEvent.payload(), envelope.payload(), "payment payload");
    }

    private static void ledgerEntriesMapToEnvelope() {
        LedgerEntry ledgerEntry = new LedgerEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                -250,
                "ZAR",
                UUID.randomUUID(),
                EntryType.DEBIT,
                UUID.randomUUID(),
                Instant.parse("2026-06-18T10:15:00Z")
        );

        EventEnvelope envelope = ledgerEntry.toEnvelope("trace-ledger-1");

        assertEquals(EventTopic.LEDGER.topicName(), envelope.topic(), "ledger topic");
        assertEquals(EventTopic.LEDGER.partitionKeyField(), envelope.partitionKeyField(), "ledger partition field");
        assertEquals(ledgerEntry.accountId().toString(), envelope.partitionKeyValue(), "ledger partition key");
        assertTrue(envelope.payload().contains("\"signedAmount\":-250"), "ledger payload should include signed amount");
    }

    private static void outboxRelayPublishesInOrder() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        RecordingPublisher publisher = new RecordingPublisher();
        InMemoryOutboxRelay relay = new InMemoryOutboxRelay(store, publisher);

        EventEnvelope first = EventEnvelopeFactory.create(
                EventTopic.PAYMENTS,
                UUID.randomUUID(),
                "Payment",
                "payment.initiated",
                Instant.parse("2026-06-18T10:00:00Z"),
                "trace-1",
                "{\"seq\":1}"
        );
        EventEnvelope second = EventEnvelopeFactory.create(
                EventTopic.PAYMENTS,
                UUID.randomUUID(),
                "Payment",
                "payment.completed",
                Instant.parse("2026-06-18T10:00:01Z"),
                "trace-1",
                "{\"seq\":2}"
        );

        store.append(first);
        store.append(second);

        InMemoryOutboxRelay.RelayResult result = relay.relayPending();

        assertEquals(2, result.publishedCount(), "published count");
        assertEquals(0, result.failedCount(), "failed count");
        assertEquals(2, publisher.published().size(), "publisher count");
        assertEquals(first.eventId(), publisher.published().get(0).eventId(), "first published");
        assertEquals(second.eventId(), publisher.published().get(1).eventId(), "second published");
        assertEquals(0, store.pending().size(), "pending count");
    }

    private static void outboxRelayRecordsPublishFailures() {
        InMemoryOutboxStore store = new InMemoryOutboxStore();
        EventEnvelope envelope = EventEnvelopeFactory.create(
                EventTopic.PAYMENTS,
                UUID.randomUUID(),
                "Payment",
                "payment.failed",
                Instant.parse("2026-06-18T10:00:00Z"),
                "trace-1",
                "{\"seq\":1}"
        );
        store.append(envelope);
        InMemoryOutboxRelay relay = new InMemoryOutboxRelay(store, ignored -> {
            throw new EventEnvelopeException("broker unavailable");
        });

        InMemoryOutboxRelay.RelayResult result = relay.relayPending();

        assertEquals(0, result.publishedCount(), "published count");
        assertEquals(1, result.failedCount(), "failed count");
        assertEquals(1, result.pendingCount(), "pending count");
        assertEquals(1, store.all().get(0).publishAttempts(), "attempt count");
        assertEquals("broker unavailable", store.all().get(0).lastError(), "last error");
    }

    private static void inMemoryPublisherRecordsPublishedEnvelopes() {
        InMemoryEventPublisher publisher = new InMemoryEventPublisher();
        EventEnvelope envelope = EventEnvelopeFactory.create(
                EventTopic.RECOVERY,
                UUID.randomUUID(),
                "RecoveryCase",
                "recovery.completed",
                Instant.parse("2026-06-18T10:00:00Z"),
                "trace-recovery-1",
                "{\"status\":\"COMPLETED\"}"
        );

        publisher.publish(envelope);

        assertEquals(1, publisher.published().size(), "publisher count");
        assertEquals(envelope.eventId(), publisher.published().get(0).eventId(), "published event");
    }
    private static void topicCatalogExposesPlanTopics() {
        assertEquals("payments", EventTopic.PAYMENTS.topicName(), "payments topic");
        assertEquals("ledger", EventTopic.LEDGER.topicName(), "ledger topic");
        assertEquals("fraud", EventTopic.FRAUD.topicName(), "fraud topic");
        assertEquals("voice", EventTopic.VOICE.topicName(), "voice topic");
        assertEquals("compliance", EventTopic.COMPLIANCE.topicName(), "compliance topic");
        assertEquals("identity", EventTopic.IDENTITY.topicName(), "identity topic");
        assertEquals("support", EventTopic.SUPPORT.topicName(), "support topic");
        assertEquals("recovery", EventTopic.RECOVERY.topicName(), "recovery topic");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private record RecordingPublisher(java.util.ArrayList<EventEnvelope> published) implements EventPublisher {
        RecordingPublisher() {
            this(new java.util.ArrayList<>());
        }

        @Override
        public void publish(EventEnvelope envelope) {
            published.add(envelope);
        }
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}

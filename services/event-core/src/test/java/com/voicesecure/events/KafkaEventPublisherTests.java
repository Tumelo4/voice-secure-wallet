package com.voicesecure.events;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KafkaEventPublisherTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("Kafka publisher maps envelopes into broker records", KafkaEventPublisherTests::mapsEnvelopesToKafkaRecords),
                new TestCase("Kafka publisher preserves event headers", KafkaEventPublisherTests::preservesEventHeaders)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Kafka event publisher tests passed: " + tests.length);
    }

    private static void mapsEnvelopesToKafkaRecords() {
        RecordingKafkaPublisher publisher = new RecordingKafkaPublisher();
        KafkaEventPublisher kafkaPublisher = new KafkaEventPublisher(publisher);
        EventEnvelope envelope = EventEnvelopeFactory.create(
                EventTopic.PAYMENTS,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "Payment",
                "payment.completed",
                Instant.parse("2026-07-06T12:00:00Z"),
                "trace-kafka-1",
                "{\"status\":\"COMPLETED\"}"
        );

        kafkaPublisher.publish(envelope);

        assertEquals(1, publisher.records().size(), "record count");
        KafkaRecord record = publisher.records().get(0);
        assertEquals("payments", record.topic(), "topic");
        assertEquals(envelope.partitionKeyValue(), record.key(), "partition key");
        assertContains(record.value(), "\"eventType\":\"payment.completed\"", "record JSON");
        assertContains(record.value(), "\"traceId\":\"trace-kafka-1\"", "trace id in JSON");
    }

    private static void preservesEventHeaders() {
        RecordingKafkaPublisher publisher = new RecordingKafkaPublisher();
        KafkaEventPublisher kafkaPublisher = new KafkaEventPublisher(publisher);
        EventEnvelope envelope = EventEnvelopeFactory.create(
                EventTopic.RECOVERY,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                "RecoveryCase",
                "recovery.completed",
                Instant.parse("2026-07-06T12:00:01Z"),
                "trace-kafka-2",
                "{\"status\":\"COMPLETED\"}"
        );

        kafkaPublisher.publish(envelope);

        KafkaRecord record = publisher.records().get(0);
        assertEquals("recovery.completed", record.headers().get("eventType"), "event type header");
        assertEquals("1.0", record.headers().get("eventVersion"), "event version header");
        assertEquals("RecoveryCase", record.headers().get("aggregateType"), "aggregate type header");
        assertEquals(EventTopic.RECOVERY.partitionKeyField(), record.headers().get("partitionKeyField"), "partition key field header");
        assertEquals("trace-kafka-2", record.headers().get("traceId"), "trace header");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            throw new AssertionError(message + ": expected to find " + expected);
        }
    }

    private record RecordingKafkaPublisher(List<KafkaRecord> records) implements KafkaRecordPublisher {
        RecordingKafkaPublisher() {
            this(new ArrayList<>());
        }

        @Override
        public void publish(KafkaRecord record) {
            records.add(record);
        }
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}

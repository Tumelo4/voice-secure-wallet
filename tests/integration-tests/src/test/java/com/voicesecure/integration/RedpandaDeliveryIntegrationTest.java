package com.voicesecure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
final class RedpandaDeliveryIntegrationTest {
    private static final String EVENTS = "payment-events";
    private static final String DEAD_LETTERS = "payment-events-dlq";

    @Container
    private static final RedpandaContainer REDPANDA = new RedpandaContainer(DockerImageName.parse(
            "docker.redpanda.com/redpandadata/redpanda:v25.1.3@sha256:ed78af37eeaf733deaf7201cb89d5317c51b6bd404447cb6fb2dfbf517b4d76c")
            .asCompatibleSubstituteFor("docker.redpanda.com/redpandadata/redpanda"));

    @BeforeAll
    static void createTopics() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(EVENTS, 1, (short) 1), new NewTopic(DEAD_LETTERS, 1, (short) 1)))
                    .all().get();
        }
    }

    @Test
    void duplicateOutOfOrderAndPoisonDeliveriesRemainFinanciallyIdempotentAcrossRestart() throws Exception {
        String group = "payment-consumer-" + UUID.randomUUID();
        try (KafkaProducer<String, String> producer = producer()) {
            send(producer, "payment-1", "{\"eventId\":\"event-2\",\"type\":\"payment.funds_reserved\",\"sequence\":2}");
            send(producer, "payment-1", "{\"eventId\":\"event-1\",\"type\":\"payment.started\",\"sequence\":1}");
            send(producer, "payment-1", "{\"eventId\":\"event-1\",\"type\":\"payment.started\",\"sequence\":1}");
            send(producer, "payment-1", "not-json");
            producer.flush();
        }

        Set<String> appliedEventIds = new HashSet<>();
        List<Integer> appliedSequences = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = consumer(group); KafkaProducer<String, String> producer = producer()) {
            consumer.subscribe(List.of(EVENTS));
            for (String value : pollValues(consumer, 4)) {
                if (!isCompatibleEvent(value)) {
                    producer.send(new ProducerRecord<>(DEAD_LETTERS, "payment-1", value)).get();
                    continue;
                }
                String eventId = jsonString(value, "eventId");
                if (appliedEventIds.add(eventId)) appliedSequences.add(jsonInt(value, "sequence"));
            }
            consumer.commitSync();
        }

        assertEquals(Set.of("event-1", "event-2"), appliedEventIds);
        assertEquals(List.of(2, 1), appliedSequences, "consumer tolerates provider order without duplicate effects");
        assertEquals(List.of("not-json"), consumeTopic(DEAD_LETTERS, "dlq-" + UUID.randomUUID(), 1));

        try (KafkaProducer<String, String> producer = producer()) {
            send(producer, "payment-1", "{\"eventId\":\"event-3\",\"type\":\"payment.completed\",\"sequence\":3}");
            producer.flush();
        }
        assertEquals(1, consumeTopic(EVENTS, group, 1).size(), "restarted consumer resumes after its committed offset");
    }

    private static KafkaProducer<String, String> producer() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("bootstrap.servers", REDPANDA.getBootstrapServers());
        properties.put("acks", "all");
        properties.put("enable.idempotence", true);
        return new KafkaProducer<>(properties, new StringSerializer(), new StringSerializer());
    }

    private static KafkaConsumer<String, String> consumer(String group) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new KafkaConsumer<>(properties, new StringDeserializer(), new StringDeserializer());
    }

    private static void send(KafkaProducer<String, String> producer, String key, String value) throws Exception {
        producer.send(new ProducerRecord<>(EVENTS, key, value)).get();
    }

    private static List<String> consumeTopic(String topic, String group, int count) {
        try (KafkaConsumer<String, String> consumer = consumer(group)) {
            consumer.subscribe(List.of(topic));
            return pollValues(consumer, count);
        }
    }

    private static List<String> pollValues(KafkaConsumer<String, String> consumer, int expected) {
        List<String> values = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (values.size() < expected && System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(250)).forEach(record -> values.add(record.value()));
        }
        assertEquals(expected, values.size(), "expected broker records before timeout");
        return values;
    }

    private static boolean isCompatibleEvent(String value) {
        return value.startsWith("{") && value.endsWith("}")
                && value.contains("\"eventId\"") && value.contains("\"type\"") && value.contains("\"sequence\"");
    }

    private static String jsonString(String value, String field) {
        String marker = "\"" + field + "\":\"";
        int start = value.indexOf(marker) + marker.length();
        int end = value.indexOf('"', start);
        return value.substring(start, end);
    }

    private static int jsonInt(String value, String field) {
        String marker = "\"" + field + "\":";
        int start = value.indexOf(marker) + marker.length();
        int end = value.indexOf('}', start);
        return Integer.parseInt(value.substring(start, end));
    }
}

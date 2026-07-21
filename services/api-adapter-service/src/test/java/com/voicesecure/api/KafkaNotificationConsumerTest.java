package com.voicesecure.api;

import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventEnvelopeCodec;
import com.voicesecure.notifications.DeterministicOtpGenerator;
import com.voicesecure.notifications.InMemoryNotificationRepository;
import com.voicesecure.notifications.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class KafkaNotificationConsumerTest {
    @Test
    void commitsOnlyAfterNotificationIsDurablyAccepted() {
        TopicPartition partition = new TopicPartition("payments", 0);
        MockConsumer<String, String> kafka = consumerAtStart(partition);
        InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
        KafkaNotificationConsumer worker = new KafkaNotificationConsumer(kafka,
                new NotificationService(repository, new DeterministicOtpGenerator("123456")));
        kafka.addRecord(new ConsumerRecord<>("payments", 0, 0, "payment-1", EventEnvelopeCodec.encode(paymentEvent())));

        assertEquals(1, worker.pollOnce(Duration.ZERO));

        assertEquals(1, repository.deliveries().size());
        assertEquals(1, kafka.committed(Set.of(partition)).get(partition).offset());
    }

    @Test
    void leavesFailedRecordUncommittedAndSeeksForRedelivery() {
        TopicPartition partition = new TopicPartition("payments", 0);
        TopicPartition laterPartition = new TopicPartition("payments", 1);
        MockConsumer<String, String> kafka = consumerAtStart(partition, laterPartition);
        KafkaNotificationConsumer worker = new KafkaNotificationConsumer(kafka,
                new NotificationService(new InMemoryNotificationRepository(), new DeterministicOtpGenerator("123456")));
        kafka.addRecord(new ConsumerRecord<>("payments", 0, 0, "broken", "not-json"));
        kafka.addRecord(new ConsumerRecord<>("payments", 1, 0, "later", EventEnvelopeCodec.encode(paymentEvent())));

        assertThrows(RuntimeException.class, () -> worker.pollOnce(Duration.ZERO));

        assertEquals(0, kafka.position(partition));
        assertEquals(0, kafka.position(laterPartition));
        assertNull(kafka.committed(Set.of(partition)).get(partition));
    }

    private static MockConsumer<String, String> consumerAtStart(TopicPartition... partitions) {
        MockConsumer<String, String> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        consumer.assign(List.of(partitions));
        Map<TopicPartition, Long> offsets = new java.util.LinkedHashMap<>();
        for (TopicPartition partition : partitions) offsets.put(partition, 0L);
        consumer.updateBeginningOffsets(offsets);
        return consumer;
    }

    private static EventEnvelope paymentEvent() {
        UUID paymentId = UUID.randomUUID();
        return new EventEnvelope(UUID.randomUUID(), "payments", "payment_id", paymentId.toString(),
                "payment.completed", "1.0", paymentId, "payment", Instant.parse("2026-07-18T18:00:00Z"),
                "trace-kafka-notification", "{\"userId\":\"customer-1\",\"amount\":100,\"currency\":\"ZAR\"}");
    }
}

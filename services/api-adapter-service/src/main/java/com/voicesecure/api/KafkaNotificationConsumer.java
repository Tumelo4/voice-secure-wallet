package com.voicesecure.api;

import com.voicesecure.events.EventEnvelopeCodec;
import com.voicesecure.notifications.NotificationService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Polls notification events and commits each offset only after durable inbox persistence. */
public final class KafkaNotificationConsumer implements AutoCloseable {
    private final Consumer<String, String> consumer;
    private final NotificationService notifications;

    public KafkaNotificationConsumer(Consumer<String, String> consumer, NotificationService notifications) {
        this.consumer = Objects.requireNonNull(consumer, "consumer");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
    }

    public int pollOnce(Duration timeout) {
        var batch = new ArrayList<ConsumerRecord<String, String>>();
        consumer.poll(Objects.requireNonNull(timeout, "timeout")).forEach(batch::add);
        int processed = 0;
        for (int index = 0; index < batch.size(); index++) {
            var record = batch.get(index);
            TopicPartition partition = new TopicPartition(record.topic(), record.partition());
            try {
                notifications.consume(EventEnvelopeCodec.decode(record.value()));
                consumer.commitSync(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)));
                processed++;
            } catch (RuntimeException exception) {
                rewindUnprocessed(batch, index);
                throw exception;
            }
        }
        return processed;
    }

    private void rewindUnprocessed(
            List<ConsumerRecord<String, String>> batch, int failedIndex) {
        Map<TopicPartition, Long> offsets = new LinkedHashMap<>();
        for (int index = failedIndex; index < batch.size(); index++) {
            var record = batch.get(index);
            offsets.putIfAbsent(new TopicPartition(record.topic(), record.partition()), record.offset());
        }
        offsets.forEach(consumer::seek);
    }

    @Override public void close() { consumer.close(); }
}

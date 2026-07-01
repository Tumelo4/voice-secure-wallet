package com.voicesecure.notifications;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class InMemoryNotificationRepository implements NotificationRepository {
    private final Set<UUID> processedEventIds = new HashSet<>();
    private final List<NotificationDelivery> deliveries = new ArrayList<>();

    @Override
    public synchronized boolean hasProcessedEvent(UUID eventId) {
        return processedEventIds.contains(eventId);
    }

    @Override
    public synchronized void markProcessedEvent(UUID eventId) {
        processedEventIds.add(eventId);
    }

    @Override
    public synchronized void save(NotificationDelivery delivery) {
        deliveries.add(delivery);
    }

    @Override
    public synchronized List<NotificationDelivery> deliveries() {
        return List.copyOf(deliveries);
    }
}

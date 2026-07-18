package com.voicesecure.notifications;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryNotificationRepository implements NotificationRepository {
    private final List<NotificationDelivery> deliveries = new ArrayList<>();

    @Override
    public synchronized NotificationDelivery saveIfUnprocessed(NotificationDelivery delivery) {
        NotificationDelivery existing = deliveries.stream()
                .filter(candidate -> candidate.sourceEventId().equals(delivery.sourceEventId()))
                .findFirst().orElse(null);
        if (existing != null) return existing;
        deliveries.add(delivery);
        return delivery;
    }

    @Override
    public synchronized List<NotificationDelivery> deliveries() {
        return List.copyOf(deliveries);
    }
}

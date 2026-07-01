package com.voicesecure.notifications;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository {
    boolean hasProcessedEvent(UUID eventId);

    void markProcessedEvent(UUID eventId);

    void save(NotificationDelivery delivery);

    List<NotificationDelivery> deliveries();
}

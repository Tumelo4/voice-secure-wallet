package com.voicesecure.notifications;

import java.util.List;

public interface NotificationRepository {
    NotificationDelivery saveIfUnprocessed(NotificationDelivery delivery);

    List<NotificationDelivery> deliveries();
}

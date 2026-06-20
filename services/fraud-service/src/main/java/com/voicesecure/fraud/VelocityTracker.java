package com.voicesecure.fraud;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VelocityTracker {
    private final Map<UUID, Deque<Instant>> events = new HashMap<>();

    public synchronized int recordAndCount(UUID userId, Instant occurredAt, Duration window) {
        Deque<Instant> userEvents = events.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        Instant floor = occurredAt.minus(window);
        while (!userEvents.isEmpty() && userEvents.peekFirst().isBefore(floor)) {
            userEvents.removeFirst();
        }
        userEvents.addLast(occurredAt);
        return userEvents.size();
    }
}

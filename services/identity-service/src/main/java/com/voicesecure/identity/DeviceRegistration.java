package com.voicesecure.identity;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeviceRegistration(
        UUID userId,
        UUID deviceId,
        PublicKey publicKey,
        Instant registeredAt,
        boolean active
) {
    public DeviceRegistration {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(registeredAt, "registeredAt");
    }
}


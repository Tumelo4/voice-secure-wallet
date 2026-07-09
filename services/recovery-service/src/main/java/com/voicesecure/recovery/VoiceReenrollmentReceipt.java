package com.voicesecure.recovery;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record VoiceReenrollmentReceipt(
        UUID userId,
        UUID recoveryId,
        boolean completed,
        String reference,
        Instant completedAt
) {
    public VoiceReenrollmentReceipt {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(recoveryId, "recoveryId");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(completedAt, "completedAt");
        if (reference.isBlank()) {
            throw new RecoveryException("voice reenrollment reference cannot be blank");
        }
    }
}

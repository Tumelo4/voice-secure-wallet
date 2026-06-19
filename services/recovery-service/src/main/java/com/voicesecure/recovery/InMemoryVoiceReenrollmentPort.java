package com.voicesecure.recovery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class InMemoryVoiceReenrollmentPort implements VoiceReenrollmentPort {
    private final List<VoiceReenrollmentReceipt> receipts = new ArrayList<>();

    @Override
    public synchronized VoiceReenrollmentReceipt reenroll(UUID userId, UUID recoveryId) {
        VoiceReenrollmentReceipt receipt = new VoiceReenrollmentReceipt(
                userId,
                recoveryId,
                true,
                "voice-reenrollment-" + recoveryId,
                Instant.now()
        );
        receipts.add(receipt);
        return receipt;
    }

    public synchronized List<VoiceReenrollmentReceipt> receipts() {
        return List.copyOf(receipts);
    }
}

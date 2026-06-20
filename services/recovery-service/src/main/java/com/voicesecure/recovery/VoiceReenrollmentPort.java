package com.voicesecure.recovery;

import java.util.UUID;

public interface VoiceReenrollmentPort {
    VoiceReenrollmentReceipt reenroll(UUID userId, UUID recoveryId);
}

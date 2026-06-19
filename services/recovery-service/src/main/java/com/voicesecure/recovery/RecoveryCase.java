package com.voicesecure.recovery;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RecoveryCase(
        UUID recoveryId,
        UUID userId,
        RecoveryCaseStatus status,
        String documentType,
        String documentChecksum,
        boolean videoKycApproved,
        boolean voiceReenrollmentCompleted,
        boolean deviceCertificateReissued,
        Instant openedAt,
        Instant updatedAt,
        Instant completedAt
) {
    public RecoveryCase {
        Objects.requireNonNull(recoveryId, "recoveryId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(openedAt, "openedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (documentType != null && documentType.isBlank()) {
            throw new RecoveryException("document type cannot be blank");
        }
        if (documentChecksum != null && documentChecksum.isBlank()) {
            throw new RecoveryException("document checksum cannot be blank");
        }
        if (status == RecoveryCaseStatus.COMPLETED && completedAt == null) {
            throw new RecoveryException("completed recovery requires a completion timestamp");
        }
        if (status != RecoveryCaseStatus.COMPLETED && completedAt != null) {
            throw new RecoveryException("only completed recoveries may have a completion timestamp");
        }
    }

    public static RecoveryCase open(UUID recoveryId, UUID userId, Instant now) {
        return new RecoveryCase(recoveryId, userId, RecoveryCaseStatus.OPEN, null, null, false, false, false, now, now, null);
    }

    public RecoveryCase withDocument(String documentType, String checksum, Instant now) {
        return new RecoveryCase(recoveryId, userId, RecoveryCaseStatus.DOCUMENT_UPLOADED, documentType, checksum, videoKycApproved, voiceReenrollmentCompleted, deviceCertificateReissued, openedAt, now, completedAt);
    }

    public RecoveryCase withVideoKyc(boolean approved, Instant now) {
        RecoveryCaseStatus nextStatus = approved ? RecoveryCaseStatus.VIDEO_KYC_PASSED : RecoveryCaseStatus.VIDEO_KYC_FAILED;
        return new RecoveryCase(recoveryId, userId, nextStatus, documentType, documentChecksum, approved, voiceReenrollmentCompleted, deviceCertificateReissued, openedAt, now, completedAt);
    }

    public RecoveryCase withVoiceReenrollmentCompleted(Instant now) {
        return new RecoveryCase(recoveryId, userId, RecoveryCaseStatus.VOICE_REENROLLED, documentType, documentChecksum, videoKycApproved, true, deviceCertificateReissued, openedAt, now, completedAt);
    }

    public RecoveryCase withDeviceCertificateReissued(Instant now) {
        return new RecoveryCase(recoveryId, userId, RecoveryCaseStatus.DEVICE_CERT_REISSUED, documentType, documentChecksum, videoKycApproved, voiceReenrollmentCompleted, true, openedAt, now, completedAt);
    }

    public RecoveryCase completed(Instant now) {
        return new RecoveryCase(recoveryId, userId, RecoveryCaseStatus.COMPLETED, documentType, documentChecksum, videoKycApproved, voiceReenrollmentCompleted, deviceCertificateReissued, openedAt, now, now);
    }
}

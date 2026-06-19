package com.voicesecure.recovery;

import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventEnvelopeFactory;
import com.voicesecure.events.EventPublisher;
import com.voicesecure.events.EventTopic;
import com.voicesecure.identity.DeviceRegistration;
import com.voicesecure.identity.IdentityService;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class RecoveryService {
    private final RecoveryRepository repository;
    private final IdentityService identityService;
    private final VoiceReenrollmentPort voiceReenrollmentPort;
    private final EventPublisher eventPublisher;

    public RecoveryService(
            RecoveryRepository repository,
            IdentityService identityService,
            VoiceReenrollmentPort voiceReenrollmentPort,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.identityService = Objects.requireNonNull(identityService, "identityService");
        this.voiceReenrollmentPort = Objects.requireNonNull(voiceReenrollmentPort, "voiceReenrollmentPort");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public RecoveryCase startRecovery(UUID userId) {
        RecoveryCase recoveryCase = RecoveryCase.open(UUID.randomUUID(), userId, Instant.now());
        repository.save(recoveryCase);
        return recoveryCase;
    }

    public RecoveryCase uploadIdentityDocument(UUID recoveryId, String documentType, String checksum) {
        RecoveryCase recoveryCase = requireRecovery(recoveryId);
        recoveryCase = recoveryCase.withDocument(documentType, checksum, Instant.now());
        repository.save(recoveryCase);
        return recoveryCase;
    }

    public RecoveryCase completeVideoKyc(UUID recoveryId, boolean approved) {
        RecoveryCase recoveryCase = requireRecovery(recoveryId);
        recoveryCase = recoveryCase.withVideoKyc(approved, Instant.now());
        repository.save(recoveryCase);
        return recoveryCase;
    }

    public VoiceReenrollmentReceipt requestVoiceReenrollment(UUID recoveryId) {
        RecoveryCase recoveryCase = requireRecovery(recoveryId);
        ensureApproved(recoveryCase);
        VoiceReenrollmentReceipt receipt = voiceReenrollmentPort.reenroll(recoveryCase.userId(), recoveryId);
        if (!receipt.completed()) {
            throw new RecoveryException("voice reenrollment did not complete");
        }
        recoveryCase = recoveryCase.withVoiceReenrollmentCompleted(Instant.now());
        repository.save(recoveryCase);
        return receipt;
    }

    public DeviceRegistration reissueDeviceCertificate(UUID recoveryId, UUID deviceId, PublicKey publicKey) {
        RecoveryCase recoveryCase = requireRecovery(recoveryId);
        ensureApproved(recoveryCase);
        DeviceRegistration registration = identityService.reissueDeviceCertificate(recoveryCase.userId(), deviceId, publicKey);
        recoveryCase = recoveryCase.withDeviceCertificateReissued(Instant.now());
        repository.save(recoveryCase);
        return registration;
    }

    public RecoveryCase completeRecovery(UUID recoveryId, String traceId) {
        RecoveryCase recoveryCase = requireRecovery(recoveryId);
        if (recoveryCase.status() == RecoveryCaseStatus.COMPLETED) {
            return recoveryCase;
        }
        ensureReadyToComplete(recoveryCase);
        RecoveryCase completed = recoveryCase.completed(Instant.now());
        repository.save(completed);
        publishCompletedEvent(completed, traceId);
        return completed;
    }

    private void publishCompletedEvent(RecoveryCase recoveryCase, String traceId) {
        EventEnvelope envelope = EventEnvelopeFactory.create(
                EventTopic.RECOVERY,
                recoveryCase.recoveryId(),
                "RecoveryCase",
                "recovery.completed",
                recoveryCase.completedAt(),
                traceId,
                payloadJson(recoveryCase)
        );
        eventPublisher.publish(envelope);
    }

    private RecoveryCase requireRecovery(UUID recoveryId) {
        return repository.findById(recoveryId)
                .orElseThrow(() -> new RecoveryException("recovery case not found: " + recoveryId));
    }

    private void ensureApproved(RecoveryCase recoveryCase) {
        if (!recoveryCase.videoKycApproved()) {
            throw new RecoveryException("video KYC must pass before recovery can continue");
        }
    }

    private void ensureReadyToComplete(RecoveryCase recoveryCase) {
        if (!recoveryCase.videoKycApproved()) {
            throw new RecoveryException("video KYC must pass before recovery can complete");
        }
        if (!recoveryCase.voiceReenrollmentCompleted()) {
            throw new RecoveryException("voice reenrollment must complete before recovery can complete");
        }
        if (!recoveryCase.deviceCertificateReissued()) {
            throw new RecoveryException("device certificate must be reissued before recovery can complete");
        }
    }

    private String payloadJson(RecoveryCase recoveryCase) {
        return "{"
                + "\"recoveryId\":\"" + escape(recoveryCase.recoveryId().toString()) + "\","
                + "\"userId\":\"" + escape(recoveryCase.userId().toString()) + "\","
                + "\"status\":\"" + escape(recoveryCase.status().name()) + "\","
                + "\"documentType\":\"" + escape(valueOrEmpty(recoveryCase.documentType())) + "\","
                + "\"documentChecksum\":\"" + escape(valueOrEmpty(recoveryCase.documentChecksum())) + "\","
                + "\"videoKycApproved\":" + recoveryCase.videoKycApproved() + ","
                + "\"voiceReenrollmentCompleted\":" + recoveryCase.voiceReenrollmentCompleted() + ","
                + "\"deviceCertificateReissued\":" + recoveryCase.deviceCertificateReissued()
                + "}";
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

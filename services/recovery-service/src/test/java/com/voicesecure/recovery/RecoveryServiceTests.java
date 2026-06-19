package com.voicesecure.recovery;

import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventTopic;
import com.voicesecure.events.InMemoryEventPublisher;
import com.voicesecure.identity.DeviceRegistration;
import com.voicesecure.identity.IdentityService;
import com.voicesecure.identity.InMemoryIdentityRepository;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.UUID;

public final class RecoveryServiceTests {
    public static void main(String[] args) throws Exception {
        TestCase[] tests = {
                new TestCase("recovery flow reissues the device certificate and publishes completion", RecoveryServiceTests::recoveryFlowReissuesDeviceCertificateAndPublishesCompletion),
                new TestCase("recovery completion is blocked until the flow is ready", RecoveryServiceTests::recoveryCompletionIsBlockedUntilReady)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Recovery service tests passed: " + tests.length);
    }

    private static void recoveryFlowReissuesDeviceCertificateAndPublishesCompletion() throws Exception {
        Fixture fixture = fixture();
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        fixture.identityService.registerDevice(userId, deviceId, fixture.originalDeviceKey.getPublic());

        RecoveryCase recoveryCase = fixture.service.startRecovery(userId);
        fixture.service.uploadIdentityDocument(recoveryCase.recoveryId(), "passport", "sha256:doc-123");
        fixture.service.completeVideoKyc(recoveryCase.recoveryId(), true);
        VoiceReenrollmentReceipt receipt = fixture.service.requestVoiceReenrollment(recoveryCase.recoveryId());
        DeviceRegistration reissued = fixture.service.reissueDeviceCertificate(recoveryCase.recoveryId(), deviceId, fixture.replacementDeviceKey.getPublic());
        RecoveryCase completed = fixture.service.completeRecovery(recoveryCase.recoveryId(), "trace-recovery-1");

        assertEquals(true, receipt.completed(), "voice reenrollment should complete");
        assertEquals(RecoveryCaseStatus.COMPLETED, completed.status(), "recovery should complete");
        assertEquals(fixture.replacementDeviceKey.getPublic(), reissued.publicKey(), "device certificate should be replaced");
        assertEquals(fixture.replacementDeviceKey.getPublic(), fixture.identityRepository.findDevice(userId, deviceId).orElseThrow().publicKey(), "identity repo should hold the new certificate");
        assertEquals(1, fixture.voicePort.receipts().size(), "voice reenrollment should be requested once");
        assertEquals(1, fixture.eventPublisher.published().size(), "completion event should be published");

        EventEnvelope envelope = fixture.eventPublisher.published().get(0);
        assertEquals(EventTopic.RECOVERY.topicName(), envelope.topic(), "recovery topic");
        assertEquals("recovery.completed", envelope.eventType(), "recovery event type");
        assertTrue(envelope.payload().contains("\"documentType\":\"passport\""), "recovery payload should include document type");
    }

    private static void recoveryCompletionIsBlockedUntilReady() throws Exception {
        Fixture fixture = fixture();
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        fixture.identityService.registerDevice(userId, deviceId, fixture.originalDeviceKey.getPublic());

        RecoveryCase recoveryCase = fixture.service.startRecovery(userId);
        fixture.service.uploadIdentityDocument(recoveryCase.recoveryId(), "passport", "sha256:doc-456");
        fixture.service.completeVideoKyc(recoveryCase.recoveryId(), true);

        assertThrows(RecoveryException.class, () -> fixture.service.completeRecovery(recoveryCase.recoveryId(), "trace-recovery-2"), "completion should be blocked before reenrollment and reissue");
    }

    private static Fixture fixture() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair signingKey = generator.generateKeyPair();
        KeyPair originalDeviceKey = generator.generateKeyPair();
        KeyPair replacementDeviceKey = generator.generateKeyPair();
        InMemoryIdentityRepository identityRepository = new InMemoryIdentityRepository();
        IdentityService identityService = new IdentityService(identityRepository, signingKey, "voice-secure-key-1");
        InMemoryRecoveryRepository recoveryRepository = new InMemoryRecoveryRepository();
        InMemoryVoiceReenrollmentPort voicePort = new InMemoryVoiceReenrollmentPort();
        InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher();
        RecoveryService service = new RecoveryService(recoveryRepository, identityService, voicePort, eventPublisher);
        return new Fixture(service, identityService, identityRepository, voicePort, eventPublisher, originalDeviceKey, replacementDeviceKey);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(Class<? extends Throwable> expected, ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return;
            }
            throw new AssertionError(message + ": expected " + expected.getSimpleName() + " but got " + actual, actual);
        }
        throw new AssertionError(message + ": expected " + expected.getSimpleName());
    }

    private record Fixture(
            RecoveryService service,
            IdentityService identityService,
            InMemoryIdentityRepository identityRepository,
            InMemoryVoiceReenrollmentPort voicePort,
            InMemoryEventPublisher eventPublisher,
            KeyPair originalDeviceKey,
            KeyPair replacementDeviceKey
    ) {
    }

    private record TestCase(String name, ThrowingRunnable runnable) {
        void run() throws Exception {
            runnable.run();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

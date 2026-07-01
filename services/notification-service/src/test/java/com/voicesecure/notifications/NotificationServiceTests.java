package com.voicesecure.notifications;

import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventEnvelopeFactory;
import com.voicesecure.events.EventTopic;
import java.time.Instant;
import java.util.UUID;

public final class NotificationServiceTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("payment completion sends receipt notification", NotificationServiceTests::paymentCompletionSendsReceipt),
                new TestCase("voice fallback sends otp challenge", NotificationServiceTests::voiceFallbackSendsOtp),
                new TestCase("notification consumption is idempotent by event id", NotificationServiceTests::consumptionIsIdempotent),
                new TestCase("notification rejects unsupported synchronous topic", NotificationServiceTests::rejectsUnsupportedTopic)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Notification service tests passed: " + tests.length);
    }

    private static void paymentCompletionSendsReceipt() {
        InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
        NotificationService service = new NotificationService(repository, new DeterministicOtpGenerator("123456"));

        service.consume(paymentEvent("payment.completed", "{\"userId\":\"user-123\",\"amount\":750,\"currency\":\"ZAR\"}"));

        NotificationDelivery delivery = repository.deliveries().get(0);
        assertEquals(NotificationChannel.PUSH, delivery.channel(), "receipt channel");
        assertEquals("payment.completed", delivery.sourceEventType(), "source event type");
        assertTrue(delivery.message().contains("ZAR 750"), "receipt should include amount");
    }

    private static void voiceFallbackSendsOtp() {
        InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
        NotificationService service = new NotificationService(repository, new DeterministicOtpGenerator("654321"));

        service.consume(voiceEvent("voice.fallback_requested", "{\"userId\":\"user-456\",\"method\":\"OTP\",\"transactionAmount\":900}"));

        NotificationDelivery delivery = repository.deliveries().get(0);
        assertEquals(NotificationChannel.OTP, delivery.channel(), "fallback channel");
        assertTrue(delivery.message().contains("654321"), "otp code should be included");
        assertTrue(delivery.message().contains("fallback"), "message should explain fallback");
    }

    private static void consumptionIsIdempotent() {
        InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
        NotificationService service = new NotificationService(repository, new DeterministicOtpGenerator("111111"));
        EventEnvelope event = paymentEvent("payment.compensated", "{\"userId\":\"user-789\",\"amount\":300,\"currency\":\"ZAR\"}");

        service.consume(event);
        service.consume(event);

        assertEquals(1, repository.deliveries().size(), "duplicate event should not duplicate notification");
    }

    private static void rejectsUnsupportedTopic() {
        NotificationService service = new NotificationService(new InMemoryNotificationRepository(), new DeterministicOtpGenerator("222222"));
        EventEnvelope ledgerEvent = EventEnvelopeFactory.create(
                EventTopic.LEDGER,
                UUID.randomUUID(),
                "LedgerEntry",
                "ledger.entry_posted",
                Instant.parse("2026-06-20T12:00:00Z"),
                "trace-ledger",
                "{\"accountId\":\"" + UUID.randomUUID() + "\",\"signedAmount\":100,\"currency\":\"ZAR\"}"
        );

        assertThrows(NotificationException.class, () -> service.consume(ledgerEvent), "ledger events should not be consumed by notification service");
    }

    private static EventEnvelope paymentEvent(String eventType, String payload) {
        return EventEnvelopeFactory.create(
                EventTopic.PAYMENTS,
                UUID.randomUUID(),
                "Payment",
                eventType,
                Instant.parse("2026-06-20T12:00:00Z"),
                "trace-payment",
                payload
        );
    }

    private static EventEnvelope voiceEvent(String eventType, String payload) {
        return EventEnvelopeFactory.create(
                EventTopic.VOICE,
                UUID.randomUUID(),
                "VoiceVerification",
                eventType,
                Instant.parse("2026-06-20T12:00:00Z"),
                "trace-voice",
                payload
        );
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

    private static void assertThrows(Class<? extends RuntimeException> expected, Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (RuntimeException ex) {
            if (expected.isInstance(ex)) {
                return;
            }
            throw new AssertionError(message + ": expected " + expected.getSimpleName() + " but got " + ex.getClass().getSimpleName(), ex);
        }
        throw new AssertionError(message + ": expected exception " + expected.getSimpleName());
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}

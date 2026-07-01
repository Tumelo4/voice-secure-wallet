package com.voicesecure.notifications;

import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventTopic;
import java.util.Objects;
import java.util.UUID;

public final class NotificationService {
    private final NotificationRepository repository;
    private final OtpGenerator otpGenerator;

    public NotificationService(NotificationRepository repository, OtpGenerator otpGenerator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.otpGenerator = Objects.requireNonNull(otpGenerator, "otpGenerator");
    }

    public NotificationDelivery consume(EventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (repository.hasProcessedEvent(envelope.eventId())) {
            return repository.deliveries().stream()
                    .filter(delivery -> delivery.sourceEventId().equals(envelope.eventId()))
                    .findFirst()
                    .orElseThrow(() -> new NotificationException("processed event has no delivery record"));
        }

        NotificationDelivery delivery = toDelivery(envelope);
        repository.save(delivery);
        repository.markProcessedEvent(envelope.eventId());
        return delivery;
    }

    private NotificationDelivery toDelivery(EventEnvelope envelope) {
        if (envelope.isForTopic(EventTopic.PAYMENTS)) {
            return paymentDelivery(envelope);
        }
        if (envelope.isForTopic(EventTopic.VOICE)) {
            return voiceDelivery(envelope);
        }
        throw new NotificationException("notification service only consumes payment and voice events");
    }

    private NotificationDelivery paymentDelivery(EventEnvelope envelope) {
        String userId = NotificationJson.stringField(envelope.payload(), "userId");
        String amount = NotificationJson.optionalRawField(envelope.payload(), "amount", "0");
        String currency = NotificationJson.optionalStringField(envelope.payload(), "currency", "ZAR");
        String message = switch (envelope.eventType()) {
            case "payment.completed" -> "Payment receipt: " + currency + " " + amount + " completed.";
            case "payment.failed" -> "Payment failed. No funds moved.";
            case "payment.compensated" -> "Payment could not complete. Funds were returned: " + currency + " " + amount + ".";
            default -> throw new NotificationException("unsupported payment event: " + envelope.eventType());
        };
        return delivery(envelope, NotificationChannel.PUSH, userId, message);
    }

    private NotificationDelivery voiceDelivery(EventEnvelope envelope) {
        if (!"voice.fallback_requested".equals(envelope.eventType())) {
            throw new NotificationException("unsupported voice event: " + envelope.eventType());
        }
        String userId = NotificationJson.stringField(envelope.payload(), "userId");
        String method = NotificationJson.optionalStringField(envelope.payload(), "method", "OTP");
        if (!"OTP".equals(method)) {
            return delivery(envelope, NotificationChannel.PUSH, userId, "Voice fallback requested. Continue with " + method + ".");
        }
        return delivery(envelope, NotificationChannel.OTP, userId, "Use OTP " + otpGenerator.generate() + " to complete fallback verification.");
    }

    private NotificationDelivery delivery(EventEnvelope envelope, NotificationChannel channel, String recipientRef, String message) {
        return new NotificationDelivery(
                UUID.randomUUID(),
                envelope.eventId(),
                envelope.eventType(),
                channel,
                recipientRef,
                envelope.traceId(),
                message,
                envelope.occurredAt()
        );
    }
}

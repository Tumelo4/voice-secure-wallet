# notification-service

Java 17 event consumer for customer receipts and voice fallback OTP messages.

## Problem Statement

Payments should not synchronously depend on push, SMS, email, or OTP delivery.
The PDF plan requires notification delivery to consume domain events so a
notification outage cannot block legitimate money movement.

## Impact

- Customers receive payment receipts and fallback prompts from event streams.
- Payment and voice services remain decoupled from delivery providers.
- Duplicate source events do not create duplicate customer notifications.

## Scope

This service consumes `payment.completed`, `payment.failed`,
`payment.compensated`, and `voice.fallback_requested` envelopes. The current
slice records deterministic deliveries and exposes an OTP generator port for
provider replacement. `PostgresNotificationRepository` atomically claims each
event in a durable consumer inbox and writes its delivery in the same database
transaction.

## Benchmark

- Payment completion should produce a receipt notification.
- Voice fallback with `OTP` should produce a fallback OTP notification.
- Replaying the same event ID should not duplicate delivery records.
- Unsupported topics should fail fast.

## How To Use It

Create the service with a repository and OTP generator, then consume envelopes:

```java
NotificationService notifications = new NotificationService(
        new InMemoryNotificationRepository(),
        new DeterministicOtpGenerator("123456")
);
notifications.consume(paymentCompletedEnvelope);
notifications.consume(voiceFallbackRequestedEnvelope);
```

The production adapter still needs Kafka consumption, provider integrations,
template storage, retry/DLQ handling, and delivery status callbacks.

Apply `V001__notification_inbox.sql` before constructing the PostgreSQL
repository. The `(consumer_name,event_id)` primary key serializes concurrent
duplicate deliveries: losing consumers return the already committed delivery
instead of producing another customer notification. The integration profile
proves this across two repository instances against disposable PostgreSQL.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

# event-core

Shared event envelope and outbox relay utilities for VoiceSecure Wallet.

This package defines the common shape for domain events across payment, ledger,
fraud, identity, voice, and compliance flows.

## Benchmark

- Envelope creation should stay under 2 ms for local domain-event mapping.
- Relaying 1,000 pending in-memory outbox messages should preserve append order.
- Failed publishes should remain pending with an incremented attempt count and
  visible last-error detail.

## How To Use It

Create an envelope, append it to an outbox store, and relay pending messages:

```java
OutboxStore store = new InMemoryOutboxStore();
EventEnvelope envelope = EventEnvelopeFactory.create(topic, aggregateId, "Payment", "payment.completed", occurredAt, traceId, payload);
store.append(envelope);
InMemoryOutboxRelay.RelayResult result = new InMemoryOutboxRelay(store, publisher).relayPending();
```

Use `RelayResult.failedCount()` and `store.pending()` to decide whether retry or
dead-letter handling is needed.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```


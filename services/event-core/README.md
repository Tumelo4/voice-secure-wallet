# event-core

Shared event envelope, Kafka publication boundary, and outbox relay utilities
for VoiceSecure Wallet.

## Problem Statement

When every service invents its own event shape, consumers break, audit trails
fracture, and delivery semantics become hard to reason about. The platform needs
one common contract for event publication and relay behavior.

## Impact

- Engineers can add new workflows without inventing a new event format each
  time.
- Consumers receive predictable, auditable messages across the system.
- The business gets a cleaner integration backbone and fewer cross-service
  defects.

## Scope

This package defines the common shape for domain events across payment, ledger,
fraud, identity, voice, and compliance flows. It also includes a Kafka record
publisher adapter so MSK-backed delivery can be wired without changing the
domain event model.

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

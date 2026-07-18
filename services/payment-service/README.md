# payment-service

Java 17 saga orchestrator for VoiceSecure Wallet.

Maturity and remaining operational gaps are tracked in the generated
[`capability status`](../../docs/product/current-capability-status.md).

## Problem Statement

Payments fail in messy ways when authorization, reservation, ledger posting,
and compensation are implemented as separate ad hoc steps. That creates partial
success states, duplicate charges, and customer-visible inconsistencies.

## Impact

- Users get a payment flow that either finishes cleanly or compensates cleanly.
- Support teams spend less time untangling half-complete transfers.
- The business lowers chargeback risk, duplicate-payment risk, and operational
  cleanup cost.

## Scope

This service coordinates fraud approval, voice verification, fallback handling,
funds reservation, ledger commit, completion, and compensation. The state
machine remains testable in memory, and a Postgres repository now captures the
durable saga snapshot and event log needed for production replay.

## Current Guarantees

- Idempotent saga start by `idempotencyKey`.
- Conflicting requests with the same `idempotencyKey` are rejected.
- Explicit state transitions for the full payment lifecycle.
- Voice failure can route to fallback when the auth policy permits it.
- Compensation is first-class and emits audit-friendly events.

## Benchmark

The saga benchmark focuses on deterministic state transitions rather than
external throughput:

- A happy-path payment should complete through fraud approval, voice approval,
  funds reservation, ledger commit, and completion without skipped states.
- An idempotent retry should return the original saga without appending events.
- A conflicting idempotent retry should fail before any transition occurs.
- Failure paths for funds reservation, ledger commit, and compensation should
  reach terminal states in one service call each.
- The executable load probe records p95 and throughput for 10,000 starts while
  verifying persistence and idempotent replay; see
  [`performance-strategy.md`](../../docs/testing/performance-strategy.md).

## How To Use It

Create the in-memory repository and service, then start a saga with a fraud
decision:

```java
PaymentSagaRepository repository = new InMemoryPaymentSagaRepository();
PaymentSagaService payments = new PaymentSagaService(repository);
PaymentSaga saga = payments.start(request, new FraudDecision(0.12, AuthPolicy.VOICE_OTP, true, ""));
payments.recordVoiceOutcome(saga.sagaId(), new VoiceOutcome(VoiceOutcomeStatus.APPROVED, 0.98, "voice matched"));
payments.markFundsReserved(saga.sagaId());
payments.startLedgerCommit(saga.sagaId());
payments.completeLedgerCommit(saga.sagaId());
payments.complete(saga.sagaId());
```

Use `recordFallbackOutcome`, `failFundsReservation`, `failLedgerCommit`, and
`failCompensation` to exercise failure branches.

For production wiring, construct `PaymentProductionRuntime` with the managed
PostgreSQL `DataSource` and Kafka `EventPublisher`. It uses
`PostgresPaymentSagaRepository`, writes each transition and its outbox row in
the same transaction, and runs a leased, retrying outbox relay. Apply migrations
through `V005__transactional_outbox.sql` before starting the runtime.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

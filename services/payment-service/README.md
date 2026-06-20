# payment-service

Java 17 saga orchestrator for VoiceSecure Wallet.

This service coordinates fraud approval, voice verification, fallback handling,
funds reservation, ledger commit, completion, and compensation. The initial
implementation is in-memory so the state machine can be tested without any
infrastructure.

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

## How To Use It

Create the in-memory repository and service, then start a saga with a fraud
decision:

```java
PaymentSagaRepository repository = new InMemoryPaymentSagaRepository();
PaymentSagaService payments = new PaymentSagaService(repository);
PaymentSaga saga = payments.start(request, new FraudDecision(0.12, AuthPolicy.VOICE_ONLY, true, ""));
payments.recordVoiceOutcome(saga.sagaId(), new VoiceOutcome(VoiceOutcomeStatus.APPROVED, 0.98, "voice matched"));
payments.markFundsReserved(saga.sagaId());
payments.startLedgerCommit(saga.sagaId());
payments.completeLedgerCommit(saga.sagaId());
payments.complete(saga.sagaId());
```

Use `recordFallbackOutcome`, `failFundsReservation`, `failLedgerCommit`, and
`failCompensation` to exercise failure branches.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```


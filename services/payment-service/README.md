# payment-service

Java 17 saga orchestrator for VoiceSecure Wallet.

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
funds reservation, ledger commit, completion, and compensation. The initial
implementation is in-memory so the state machine can be tested without any
infrastructure.

## Current Guarantees

- Idempotent saga start by `idempotencyKey`.
- Explicit state transitions for the full payment lifecycle.
- Voice failure can route to fallback when the auth policy permits it.
- Compensation is first-class and emits audit-friendly events.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

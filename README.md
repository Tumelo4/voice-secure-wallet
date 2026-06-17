# VoiceSecure Wallet

Production-grade fintech platform scaffold for the VoiceSecure Wallet build plan.

This branch starts with Phase 1: the signed ledger core. The ledger is implemented
first because every payment, repair, and audit workflow depends on the invariant
that `SUM(signed_amount) = 0` across append-only ledger entries.

## Current Slice

- Java 17 `ledger-service` domain model.
- Java 17 `payment-service` saga core.
- Java 17 `identity-service`, `compliance-service`, and `fraud-service` cores.
- PostgreSQL schema migration for signed, append-only ledger entries.
- In-memory repository for deterministic local tests.
- Repair API domain stub requiring a justification payload.
- Lightweight Java test runner covering reconciliation, idempotency, repairs,
  concurrent overdraft prevention, payment saga transitions, and trust-layer
  checks.

## Run Tests

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-services.ps1
```

# ledger-service

Java 17 financial core for VoiceSecure Wallet.

## Problem Statement

Money movement needs a single, trusted source of truth. Without a strict ledger
model, the platform can drift into silent balance corruption, duplicate posting,
or reconciliation gaps that are expensive to unwind and hard to defend in audit.

## Impact

- Users get accurate balances and a clear trail for every transfer or repair.
- Finance and operations can reconcile the system without manual guesswork.
- The business reduces audit exposure, settlement risk, and long-running support
  escalations.

## Scope

This service owns the signed ledger. It is intentionally small in this first
slice: the domain logic, append-only repository contract, PostgreSQL migration,
and tests are present before HTTP, Kafka, or persistence adapters are added.

## Invariants

- Every accepted ledger batch sums to zero.
- `signed_amount` can never be zero.
- Debits are negative, credits are positive.
- Account balances cannot be overdrawn by normal transfers.
- Repair requires a human justification and appends new entries.
- Idempotency keys return the original accepted batch.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

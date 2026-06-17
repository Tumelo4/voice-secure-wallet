# ledger-service

Java 17 financial core for VoiceSecure Wallet.

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
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-ledger-service.ps1
```

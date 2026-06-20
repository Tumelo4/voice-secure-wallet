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
- Idempotency keys cannot be reused for a different command shape.

## Benchmark

The first service-level benchmark target is deterministic correctness under
local load:

- Reconciliation for 10,000 ledger entries should remain under 250 ms on a
  developer laptop.
- Ten concurrent debit attempts against a funded account should never overdraw
  the source balance.
- Idempotent retries should return the original batch in constant time.
- Conflicting idempotency retries should fail before any new entry is appended.

## How To Use It

Create a repository and service, then create accounts before moving money:

```java
InMemoryLedgerRepository repository = new InMemoryLedgerRepository();
LedgerService ledger = new LedgerService(repository);
ledger.createAccount(sourceAccountId, "ZAR", 1_000);
ledger.createAccount(destinationAccountId, "ZAR", 0);
LedgerBatch batch = ledger.transfer(sagaId, idempotencyKey, sourceAccountId, destinationAccountId, 250, "ZAR");
```

Use `RepairRequest` for corrective entries. A repair must include balanced
postings, a requester, and a meaningful justification.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-ledger-service.ps1
```

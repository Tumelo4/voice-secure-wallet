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

This service owns the signed ledger. The domain logic stays isolated, while
the Postgres-backed repository gives the service a production path with
append-only storage, idempotency, and durable outbox records.

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

For production wiring, use `PostgresLedgerRepository` with a `DataSource`
pointing at AWS RDS PostgreSQL. The repository expects the `ledger_batches`,
`ledger_entries`, and `outbox_events` schema from the production migration.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

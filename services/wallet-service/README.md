# wallet-service

Java 17 CQRS read model for user-facing wallet balances.

## Problem Statement

Wallet balance reads become risky when user-facing screens query ledger tables
directly or invent their own balance math. The PDF plan requires wallet reads to
be fast and customer-friendly while keeping ledger writes inside
`ledger-service` only.

## Impact

- Users can see account balances without touching the financial write path.
- Engineers keep a hard boundary between ledger authority and wallet display.
- Duplicate ledger events do not double-count balances in the read model.

## Scope

This service registers wallet metadata and projects `ledger.entry_posted`
events into account balances. It deliberately has no dependency on
`LedgerService`, preserving the plan rule that only `ledger-service` writes
ledger data.

## Benchmark

- Ledger projection should be idempotent by event ID.
- Balance reads should be simple repository lookups.
- Non-ledger events should fail fast instead of silently polluting the read
  model.

## How To Use It

Create a wallet account, then project ledger events from the event backbone:

```java
WalletService wallet = new WalletService(new InMemoryWalletRepository());
wallet.openWallet(userId, accountId, "Everyday wallet", "ZAR");
wallet.projectLedgerEntry(ledgerEntry.toEnvelope("trace-wallet-1"));
WalletBalance balance = wallet.balance(accountId);
```

The production adapter still needs Kafka consumption, durable storage, and API
read endpoints.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

# Phase 1 Ledger Core

This implementation slice follows the amended VoiceSecure Wallet plan:

- signed ledger entries use `signed_amount` where debits are negative and credits
  are positive;
- ledger entries are append-only;
- every ledger write must be balanced before it is accepted;
- account balances are updated atomically with ledger entries;
- repair is a first-class flow and requires a mandatory justification;
- reconciliation is a hard invariant, not an operational best effort.

The local Java implementation uses an in-memory repository so the invariant can be
tested without requiring PostgreSQL or Kafka on a fresh workstation. The SQL
migration in `services/ledger-service/src/main/resources/db/migration` captures
the production schema shape and append-only trigger.

## First Exit-Gate Coverage

- `SUM(signed_amount) = 0` after normal transfers.
- Zero and unbalanced postings are rejected.
- Repeated idempotency keys return the original ledger batch.
- Concurrent payment attempts cannot overdraft the source account.
- Repair writes are balanced, append-only, and audit-backed.

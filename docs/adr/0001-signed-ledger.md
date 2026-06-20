# ADR 0001: Signed Append-Only Ledger

## Status

Accepted.

## Context

The amended build plan replaces unsigned amount plus entry type accounting with a
single `signed_amount` field. Debits are negative and credits are positive. This
makes the financial invariant directly queryable:

```sql
SELECT COALESCE(SUM(signed_amount), 0) FROM ledger_entries;
```

The result must always be zero.

## Decision

`ledger-service` is the only write path for ledger entries. It accepts a batch of
postings only when the batch total is zero, every amount is non-zero, and every
affected balance remains valid. Corrections are represented as new repair entries;
existing ledger rows are never updated or deleted.

## Consequences

- Reconciliation is simple and deterministic.
- Repair flows preserve audit history.
- Services that display balances must consume ledger events or read approved
  projections rather than writing financial state themselves.

# ADR-003: Double-entry ledger

Status: Accepted

The ledger is the source of financial truth. Every journal contains at least two postings and total signed debits equal total signed credits. Entries are append-only; corrections use linked compensating entries. Idempotency is bound to the complete posting command, and concurrent debits must enforce the permitted balance atomically. Wallet balances are projections and must be reconstructable by replay.


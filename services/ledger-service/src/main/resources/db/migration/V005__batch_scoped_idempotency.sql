-- Idempotency belongs to the journal batch. A balanced batch necessarily has
-- multiple entries sharing the same command key, so entry-level uniqueness is
-- both redundant and incompatible with double entry.
ALTER TABLE ledger_entries
    DROP CONSTRAINT IF EXISTS ledger_entries_idempotency_key_key;

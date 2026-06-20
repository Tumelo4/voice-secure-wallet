CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    owner_user_id UUID,
    wallet_type TEXT NOT NULL DEFAULT 'SPENDING',
    currency TEXT NOT NULL DEFAULT 'ZAR',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT wallet_type_known CHECK (wallet_type IN ('SPENDING', 'ESCROW', 'SAVINGS', 'SYSTEM')),
    CONSTRAINT account_status_known CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

CREATE TABLE account_balances (
    account_id UUID PRIMARY KEY REFERENCES accounts(id),
    balance BIGINT NOT NULL DEFAULT 0,
    currency TEXT NOT NULL DEFAULT 'ZAR',
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT non_negative_balance CHECK (balance >= 0)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    signed_amount BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'ZAR',
    saga_id UUID NOT NULL,
    entry_type TEXT NOT NULL,
    idempotency_key UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT signed_amount_nonzero CHECK (signed_amount != 0),
    CONSTRAINT entry_type_known CHECK (
        entry_type IN ('DEBIT', 'CREDIT', 'REPAIR_DEBIT', 'REPAIR_CREDIT')
    )
);

CREATE INDEX ledger_entries_account_created_idx
    ON ledger_entries (account_id, created_at);

CREATE INDEX ledger_entries_saga_idx
    ON ledger_entries (saga_id);

CREATE INDEX ledger_entries_idempotency_idx
    ON ledger_entries (idempotency_key);

CREATE TABLE ledger_repair_audit (
    id UUID PRIMARY KEY,
    repair_id UUID NOT NULL,
    saga_id UUID NOT NULL,
    idempotency_key UUID NOT NULL,
    requested_by TEXT NOT NULL,
    justification TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT repair_justification_present CHECK (length(trim(justification)) >= 12)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type TEXT NOT NULL,
    event_type TEXT NOT NULL,
    event_version TEXT NOT NULL DEFAULT '1.0',
    payload JSONB NOT NULL,
    trace_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX outbox_events_unpublished_idx
    ON outbox_events (created_at)
    WHERE published_at IS NULL;

CREATE OR REPLACE FUNCTION prevent_ledger_entry_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only; use repair entries for corrections';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entries_append_only_update
    BEFORE UPDATE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_entry_mutation();

CREATE TRIGGER ledger_entries_append_only_delete
    BEFORE DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_entry_mutation();

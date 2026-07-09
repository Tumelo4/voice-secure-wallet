CREATE TABLE ledger_batches (
    idempotency_key UUID PRIMARY KEY,
    saga_id UUID NOT NULL,
    currency TEXT NOT NULL,
    batch_kind TEXT NOT NULL,
    repair_id UUID,
    justification TEXT,
    requested_by TEXT,
    command_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ledger_batch_kind_known CHECK (batch_kind IN ('TRANSFER', 'REPAIR'))
);

ALTER TABLE ledger_entries
    ADD COLUMN IF NOT EXISTS batch_id UUID;

ALTER TABLE ledger_entries
    ADD COLUMN IF NOT EXISTS entry_position INTEGER;

ALTER TABLE ledger_entries
    ADD CONSTRAINT ledger_entries_batch_fk
    FOREIGN KEY (batch_id) REFERENCES ledger_batches(idempotency_key);

ALTER TABLE ledger_entries
    ADD CONSTRAINT ledger_entries_batch_position_key UNIQUE (batch_id, entry_position);

ALTER TABLE ledger_entries
    ADD CONSTRAINT ledger_entries_idempotency_key_key UNIQUE (idempotency_key);

ALTER TABLE ledger_entries
    ADD CONSTRAINT ledger_entries_entry_position_positive CHECK (entry_position > 0);

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS batch_id UUID;

ALTER TABLE outbox_events
    ALTER COLUMN payload TYPE TEXT USING payload::text;

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS publish_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS last_error TEXT;

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_batch_fk
    FOREIGN KEY (batch_id) REFERENCES ledger_batches(idempotency_key);

CREATE INDEX IF NOT EXISTS ledger_entries_batch_created_idx
    ON ledger_entries (batch_id, entry_position);

CREATE INDEX IF NOT EXISTS outbox_events_batch_created_idx
    ON outbox_events (batch_id, created_at);

CREATE OR REPLACE FUNCTION prevent_ledger_batch_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger_batches is append-only; use repair entries for corrections';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_batches_append_only_update
    BEFORE UPDATE ON ledger_batches
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_batch_mutation();

CREATE TRIGGER ledger_batches_append_only_delete
    BEFORE DELETE ON ledger_batches
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_batch_mutation();

CREATE OR REPLACE FUNCTION validate_ledger_batch_balance()
RETURNS TRIGGER AS $$
DECLARE
    batch_total BIGINT;
    affected_batch UUID;
BEGIN
    affected_batch := COALESCE(NEW.batch_id, OLD.batch_id);
    SELECT COALESCE(SUM(signed_amount), 0)
    INTO batch_total
    FROM ledger_entries
    WHERE batch_id = affected_batch;

    IF batch_total <> 0 THEN
        RAISE EXCEPTION 'ledger batch % must balance to zero', affected_batch;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER ledger_entries_balance_check
    AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validate_ledger_batch_balance();

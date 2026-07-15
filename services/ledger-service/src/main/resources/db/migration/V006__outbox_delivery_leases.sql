ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS lease_owner UUID,
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS outbox_events_delivery_idx
    ON outbox_events (next_attempt_at, created_at)
    WHERE published_at IS NULL;

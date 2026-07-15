ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS dead_lettered_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS dead_letter_reason TEXT;

CREATE INDEX IF NOT EXISTS outbox_events_poison_idx
    ON outbox_events (dead_lettered_at)
    WHERE dead_lettered_at IS NOT NULL;

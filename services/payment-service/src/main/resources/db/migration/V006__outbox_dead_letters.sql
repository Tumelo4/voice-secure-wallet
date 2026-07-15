ALTER TABLE payment_outbox_events
    ADD COLUMN IF NOT EXISTS dead_lettered_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS dead_letter_reason TEXT;

CREATE INDEX IF NOT EXISTS payment_outbox_events_poison_idx
    ON payment_outbox_events (dead_lettered_at)
    WHERE dead_lettered_at IS NOT NULL;

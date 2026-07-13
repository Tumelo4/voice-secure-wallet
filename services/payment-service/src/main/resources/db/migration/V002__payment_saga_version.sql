ALTER TABLE payment_sagas
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payment_sagas
    ADD CONSTRAINT payment_saga_version_non_negative CHECK (version >= 0);

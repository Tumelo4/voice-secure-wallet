CREATE TABLE payment_recovery_audit (
    id UUID PRIMARY KEY,
    saga_id UUID NOT NULL,
    observed_state TEXT NOT NULL,
    recovery_action TEXT NOT NULL,
    trace_id TEXT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    age_millis BIGINT NOT NULL CHECK (age_millis >= 0)
);

CREATE INDEX payment_recovery_audit_saga_time_idx
    ON payment_recovery_audit (saga_id, observed_at DESC);

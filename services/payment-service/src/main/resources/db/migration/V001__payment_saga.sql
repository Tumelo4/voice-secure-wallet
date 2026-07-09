CREATE TABLE payment_sagas (
    saga_id UUID PRIMARY KEY,
    idempotency_key UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    from_account_id UUID NOT NULL,
    to_account_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    currency TEXT NOT NULL,
    trace_id TEXT NOT NULL,
    state TEXT NOT NULL,
    state_history TEXT NOT NULL,
    fraud_score DOUBLE PRECISION NOT NULL DEFAULT 0,
    auth_policy TEXT,
    fallback_method TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    persisted_event_count BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT payment_saga_amount_positive CHECK (amount > 0),
    CONSTRAINT payment_saga_state_known CHECK (
        state IN (
            'INITIATED',
            'FRAUD_CHECK_PENDING',
            'FRAUD_REJECTED',
            'VOICE_VERIFICATION_PENDING',
            'VOICE_VERIFICATION_TIMEOUT',
            'VOICE_REJECTED',
            'VOICE_FALLBACK_PENDING',
            'VOICE_FALLBACK_VERIFIED',
            'VOICE_FALLBACK_FAILED',
            'FUNDS_RESERVING',
            'FUNDS_RESERVATION_FAILED',
            'FUNDS_RESERVED',
            'LEDGER_COMMITTING',
            'LEDGER_COMMIT_FAILED',
            'LEDGER_COMMITTED',
            'COMPLETING',
            'COMPLETED',
            'COMPENSATION_IN_PROGRESS',
            'COMPENSATED',
            'COMPENSATION_FAILED'
        )
    ),
    CONSTRAINT payment_saga_auth_policy_known CHECK (
        auth_policy IN ('VOICE_ONLY', 'VOICE_OTP', 'DEVICE_PIN') OR auth_policy IS NULL
    ),
    CONSTRAINT payment_saga_fallback_method_known CHECK (
        fallback_method IN ('OTP', 'PIN', 'HARDWARE_KEY') OR fallback_method IS NULL
    )
);

CREATE TABLE payment_saga_events (
    event_id UUID PRIMARY KEY,
    saga_id UUID NOT NULL REFERENCES payment_sagas(saga_id) ON DELETE CASCADE,
    event_sequence BIGINT NOT NULL,
    event_type TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    trace_id TEXT NOT NULL,
    payload TEXT NOT NULL,
    CONSTRAINT payment_saga_events_sequence_key UNIQUE (saga_id, event_sequence)
);

CREATE INDEX payment_saga_events_saga_idx
    ON payment_saga_events (saga_id, event_sequence);

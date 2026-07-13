ALTER TABLE account_balances
    ADD COLUMN IF NOT EXISTS reserved_balance BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pending_balance BIGINT NOT NULL DEFAULT 0;

ALTER TABLE account_balances
    ADD CONSTRAINT reserved_balance_non_negative CHECK (reserved_balance >= 0),
    ADD CONSTRAINT pending_balance_non_negative CHECK (pending_balance >= 0),
    ADD CONSTRAINT reserved_not_above_balance CHECK (reserved_balance <= balance);

CREATE TABLE fund_reservations (
    reservation_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id),
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'CONSUMED', 'RELEASED', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX fund_reservations_active_payment_idx
    ON fund_reservations(payment_id) WHERE status = 'ACTIVE';
CREATE INDEX fund_reservations_expiry_idx
    ON fund_reservations(expires_at) WHERE status = 'ACTIVE';

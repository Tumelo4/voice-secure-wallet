CREATE TABLE wallet_accounts (
    account_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    display_name TEXT NOT NULL,
    currency CHAR(3) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX wallet_accounts_user_opened_idx ON wallet_accounts (user_id, opened_at, account_id);

CREATE TABLE wallet_balances (
    account_id UUID PRIMARY KEY REFERENCES wallet_accounts(account_id),
    currency CHAR(3) NOT NULL,
    balance BIGINT NOT NULL CHECK (balance >= 0),
    version BIGINT NOT NULL CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE wallet_processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

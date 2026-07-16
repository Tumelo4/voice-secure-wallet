CREATE TABLE beneficiaries (
    beneficiary_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    destination_account_id UUID NOT NULL,
    display_name TEXT NOT NULL,
    masked_account_number TEXT NOT NULL,
    currency CHAR(3) NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('COOLING_OFF', 'ACTIVE', 'BLOCKED')),
    available_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (customer_id, destination_account_id)
);
CREATE INDEX beneficiaries_customer_created_idx ON beneficiaries (customer_id, created_at, beneficiary_id);

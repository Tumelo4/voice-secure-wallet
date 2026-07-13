CREATE TABLE customer_payment_references (
    payment_reference VARCHAR(32) PRIMARY KEY,
    saga_id UUID NOT NULL UNIQUE REFERENCES payment_sagas(saga_id),
    customer_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_customer_payment_references_customer
    ON customer_payment_references (customer_id, created_at DESC);

REVOKE UPDATE, DELETE ON customer_payment_references FROM PUBLIC;

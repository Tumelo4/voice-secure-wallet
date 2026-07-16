CREATE TABLE support_transactions (entry_id UUID PRIMARY KEY, transaction_id UUID NOT NULL, account_id UUID NOT NULL,
 signed_amount BIGINT NOT NULL, currency CHAR(3) NOT NULL, entry_type TEXT NOT NULL, posted_at TIMESTAMPTZ NOT NULL);
CREATE INDEX support_transactions_search_idx ON support_transactions(account_id, currency, posted_at);
CREATE TABLE support_cases (case_id UUID PRIMARY KEY, type TEXT NOT NULL, status TEXT NOT NULL, subject_id UUID NOT NULL,
 transaction_id UUID, linked_repair_id UUID, reason TEXT NOT NULL, opened_by TEXT NOT NULL,
 opened_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL);
CREATE TABLE support_audit (audit_id UUID PRIMARY KEY, case_id UUID NOT NULL REFERENCES support_cases(case_id), action TEXT NOT NULL,
 actor TEXT NOT NULL, details TEXT NOT NULL, occurred_at TIMESTAMPTZ NOT NULL);
CREATE TABLE support_repairs (repair_id UUID PRIMARY KEY, case_id UUID NOT NULL, saga_id UUID NOT NULL, currency CHAR(3) NOT NULL,
 justification TEXT NOT NULL, requested_by TEXT NOT NULL, status TEXT NOT NULL, approved_by TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL);
CREATE TABLE support_repair_postings (repair_id UUID NOT NULL REFERENCES support_repairs(repair_id) ON DELETE CASCADE,
 position INTEGER NOT NULL, account_id UUID NOT NULL, signed_amount BIGINT NOT NULL, entry_type TEXT NOT NULL,
 PRIMARY KEY(repair_id, position));

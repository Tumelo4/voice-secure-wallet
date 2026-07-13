ALTER TABLE ledger_repair_audit
    ADD COLUMN IF NOT EXISTS approved_by TEXT;

UPDATE ledger_repair_audit
SET approved_by = requested_by || ':legacy-review-required'
WHERE approved_by IS NULL;

ALTER TABLE ledger_repair_audit
    ALTER COLUMN approved_by SET NOT NULL;

ALTER TABLE ledger_repair_audit
    ADD CONSTRAINT repair_approver_present CHECK (length(trim(approved_by)) > 0),
    ADD CONSTRAINT repair_maker_checker_distinct CHECK (trim(approved_by) <> trim(requested_by));

#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${PG_BIN:-}" ]]; then
    pg_bin="$PG_BIN"
elif command -v pg_config >/dev/null 2>&1; then
    pg_bin="$(pg_config --bindir)"
elif command -v brew >/dev/null 2>&1; then
    pg_bin="$(brew --prefix postgresql@16)/bin"
else
    echo "PostgreSQL client tools are required" >&2
    exit 1
fi
database="${PG_DATABASE:-voice_secure_migrations_$$}"

cleanup() {
    "$pg_bin/dropdb" --if-exists "$database" >/dev/null 2>&1 || true
}

trap cleanup EXIT

"$pg_bin/dropdb" --if-exists "$database"
"$pg_bin/createdb" "$database"

run_sql() {
    local sql_file="$1"
    "$pg_bin/psql" -v ON_ERROR_STOP=1 -d "$database" -f "$sql_file"
}

run_sql services/ledger-service/src/main/resources/db/migration/V001__ledger_core.sql
run_sql services/ledger-service/src/main/resources/db/migration/V002__ledger_production.sql
run_sql services/ledger-service/src/main/resources/db/migration/V003__fund_reservations.sql
run_sql services/ledger-service/src/main/resources/db/migration/V004__repair_dual_control.sql
run_sql services/payment-service/src/main/resources/db/migration/V001__payment_saga.sql
run_sql services/payment-service/src/main/resources/db/migration/V002__payment_saga_version.sql
run_sql services/payment-service/src/main/resources/db/migration/V003__payment_reconciliation_states.sql
run_sql services/payment-service/src/main/resources/db/migration/V004__customer_payment_references.sql

"$pg_bin/psql" -d "$database" -c '\dt'
echo "PostgreSQL migration smoke test passed"

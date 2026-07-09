#!/usr/bin/env bash
set -euo pipefail

pg_bin="${PG_BIN:-$(brew --prefix postgresql@16)/bin}"
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
run_sql services/payment-service/src/main/resources/db/migration/V001__payment_saga.sql

"$pg_bin/psql" -d "$database" -c '\dt'
echo "PostgreSQL migration smoke test passed"

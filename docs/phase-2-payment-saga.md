# Phase 2 Payment Saga Core

This slice implements the next major piece of the amended build plan: the
payment-service saga that orchestrates fraud approval, voice verification,
fallback handling, funds reservation, ledger commit, completion, and
compensation.

## Covered States

- `INITIATED`
- `FRAUD_CHECK_PENDING`
- `FRAUD_REJECTED`
- `VOICE_VERIFICATION_PENDING`
- `VOICE_VERIFICATION_TIMEOUT`
- `VOICE_REJECTED`
- `VOICE_FALLBACK_PENDING`
- `VOICE_FALLBACK_VERIFIED`
- `VOICE_FALLBACK_FAILED`
- `FUNDS_RESERVING`
- `FUNDS_RESERVATION_FAILED`
- `FUNDS_RESERVED`
- `LEDGER_COMMITTING`
- `LEDGER_COMMIT_FAILED`
- `LEDGER_COMMITTED`
- `COMPLETING`
- `COMPLETED`
- `COMPENSATION_IN_PROGRESS`
- `COMPENSATED`
- `COMPENSATION_FAILED`

## Intent

The saga is written as a deterministic in-memory implementation with an
append-only event trail. That keeps the branch testable while we layer in the
event bus and persistence adapters later.

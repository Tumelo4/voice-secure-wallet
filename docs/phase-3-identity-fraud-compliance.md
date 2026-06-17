# Phase 3 Identity, Fraud, and Compliance

This slice covers the trust layer described in the build plan:

- `identity-service` for device registration, JWT issuance, refresh-family
  rotation, and critical-request signature validation.
- `compliance-service` for PEP, sanctions, and AML screening with a separate
  audit trail.
- `fraud-service` for rules-based and score-based authorization policy output.

The implementation is intentionally in-memory for now, but it keeps the service
boundaries and invariants explicit so the adapters can be added later without
changing the domain behavior.


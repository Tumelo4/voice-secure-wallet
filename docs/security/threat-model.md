# Threat model

Primary threats are account-object substitution, token theft/reuse, payment replay, idempotency-key collision, concurrent overspend, voice replay/deepfake, event duplication/reordering, privileged repair abuse, sensitive-data leakage and supply-chain compromise.

Controls include OIDC validation, server-derived identity, object ownership checks, device-bound rotating sessions, request fingerprints, database locking/versioning, bounded single-use voice challenges with liveness, immutable balanced journals, maker-checker repairs, safe error contracts, secret scanning, signed artifacts and least-privilege access. Residual biometric and provider risks require fallback MFA, limits, reconciliation and manual review.


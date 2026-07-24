# Remediation plan traceability

This matrix is the completion audit for the engineering remediation plan. A row is `Proven` only when a repository artifact and an executable check directly cover the acceptance criteria. Operational claims remain explicitly separate from repository implementation.

| Phase | Status | Direct evidence | Remaining operational qualification |
|---|---|---|---|
| 0 — Baseline | Proven | `docs/architecture/current-*`, `module-catalogue.md`, `docs/testing/current-test-baseline.md`, clean Maven reactor | None for repository baseline |
| 1 — Mobile journey | Proven | Customer-safe account and beneficiary APIs, review/result screens, hidden retry key, submission guard/disabled state, React Native screen tests | Physical-device accessibility QA is not claimed |
| 2 — Public payment API | Proven | `/v1/payments` OpenAPI, authenticated principal ownership checks, request-bound idempotency, safe errors, Redocly CI | None for repository contract |
| 3 — Money and identifiers | Proven | `Money`, decimal-string boundary, currency/scale/limit tests, server-generated UUIDs | Multi-currency rollout remains intentionally unsupported |
| 4 — Architecture | Proven | ADR-001, module catalogue, one Java runtime image, package boundary tests, validators classified outside business services | Directory names retain historical `-service` suffixes but are explicitly classified as modules |
| 5 — Orchestration | Proven | Versioned durable saga repository, append-only history, outbox schema, recovery scan, reconciliation/manual-review states and invariant tests | Provider-specific reconciliation adapters are deployment integrations |
| 6 — Ledger/reconciliation | Proven | Immutable migration triggers, reservations/available balance, concurrent debit test, replay/reconstruction, dual-control repair, reconciliation docs | Live settlement files and end-of-day operations require a provider/environment |
| 7 — Identity/voice security | Proven | RS256 issuer/audience/time/key validation, refresh reuse/device tests, scopes/object authorization, transaction-bound single-use voice challenges, liveness/replay/confidence/value policy, MFA fallback | Independent biometric evaluation is not claimed |
| 8 — Testing | Proven for repository gates | Disposable PostgreSQL and Redpanda Testcontainers cover migrations, immutable ledger writes, restart/optimistic locking, duplicates, out-of-order records, poison/DLQ handling and consumer restart; contracts, mutation scores, mobile screen tests, resilience invariants and stored payment load artifacts cover the remaining gates | Device lab and external-provider chaos remain staging evidence, not repository claims |
| 9 — CI/supply chain | Proven as pipeline | Independent pinned-action jobs, CodeQL/Gitleaks/dependency/static/IaC checks, image scan, CycloneDX, provenance, digest publication and keyless Cosign signing | Release artifacts are produced only after merge to `main` |
| 10 — Documentation | Proven | Required architecture, ADR, security, operations, testing, compliance and product-status documents plus root README | Compliance assessments and production drill evidence remain honestly unclaimed |

## Verification commands

The authoritative command set is maintained in the root README and GitHub workflows. Before merge, the pull request must show every required workflow green. After merge, the `publish-by-digest` job is the evidence source for signed immutable release artifacts.

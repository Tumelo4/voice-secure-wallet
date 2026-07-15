# Current capability status

Status vocabulary is deliberately strict: **not started**, **stubbed / trust boundary not yet production-grade**, or **implemented + tested adversarially**.

| Capability | Status | Evidence / qualification |
|---|---|---|
| Mobile payment intent | Implemented prototype | Customer identity/internal saga fields removed; authenticated account and beneficiary selection; decimal-string input tests |
| Authenticated account ownership | Unit-tested | API/runtime and cross-customer tests |
| Beneficiary management | Implemented prototype | Owned beneficiary aggregate, masked list/create APIs, verification and cooling-off policy |
| Payment review and receipt journey | Screen-tested prototype | Real React Native interaction test covers owned selections, mandatory review, server-created pending payment, voice callback polling and completed receipt |
| Idempotent payment start | Integration-tested | Hidden client header, conflict detection and durable saga/reference schema; multi-instance database load testing remains operational evidence |
| Payment saga | Implemented prototype | Versioned snapshots, recovery scan, reconciliation/manual-review states; production runtime wiring incomplete |
| Double-entry ledger | Unit-tested | Balance/idempotency/concurrency tests |
| PostgreSQL ledger | Testcontainers integration-tested | PostgreSQL 16 migrations, durable posting/reconstruction, append-only triggers and payment restart/optimistic locking run against disposable real databases |
| Kafka event adapter | Testcontainers integration-tested | Pinned Redpanda covers produce/consume, duplicate delivery, out-of-order records, poison/DLQ handling and consumer restart |
| Transactional outbox | Prototype | In-memory outbox exists; production relay evidence absent |
| Voice verification | Stubbed / server-side trust boundary, not production-ready | Client submits raw audio only. A server-owned `VoiceInferenceAdapter` computes embedding, liveness, spoof and fingerprint signals; forged client scores are rejected and synthetic/replayed samples have adversarial tests. The bundled conservative feature extractor is not an independently validated biometric or anti-spoof model. |
| JWT access tokens and rotation | Implemented + tested adversarially | Nimbus JOSE+JWT enforces RS256 and known `kid`; complex Unicode claims, malformed compact tokens, overlap verification, retired-key rejection and multi-key JWKS are tested. |
| API HTTP runtime | Implemented + tested adversarially | Javalin/Jetty replaces the JDK development listener; existing auth, rate-limit, error, health and socket contract tests run against the production-capable transport. |
| Mobile secure storage | Implemented abstraction | Physical-device QA pending |
| AWS infrastructure | Planned/configured | Terraform and static validators; deployment evidence absent |
| Disaster recovery | Documented/validated as policy | No drill report |
| Reconciliation | Implemented prototype | Stuck-payment scan, uncertain-result states and manual review are tested; live provider settlement-file integration remains unproven |
| PCI DSS | Not assessed | No completed scope assessment |
| POPIA/FICA controls | Partial documentation/domain logic | Legal/compliance validation not evidenced |
| Production readiness | Not started | Signing/SBOM workflows exist, but no production release, live SLO evidence, independent biometric evaluation or recovery drill. Voice branding must not imply production biometric assurance while the interim adapter remains active. |

Terms such as “production”, “durable” and “ready” in legacy phase documents describe design targets or validator inputs unless accompanied by deployment evidence.

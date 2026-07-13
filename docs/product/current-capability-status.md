# Current capability status

| Capability | Status | Evidence / qualification |
|---|---|---|
| Mobile payment intent | Implemented prototype | Customer identity/internal saga fields removed; authenticated account and beneficiary selection; decimal-string input tests |
| Authenticated account ownership | Unit-tested | API/runtime and cross-customer tests |
| Beneficiary management | Implemented prototype | Owned beneficiary aggregate, masked list/create APIs, verification and cooling-off policy |
| Payment review and receipt journey | Screen-tested prototype | Real React Native interaction test covers owned selections, mandatory review, server-created pending payment, voice callback polling and completed receipt |
| Idempotent payment start | Integration-tested | Hidden client header, conflict detection and durable saga/reference schema; multi-instance database load testing remains operational evidence |
| Payment saga | Implemented prototype | Versioned snapshots, recovery scan, reconciliation/manual-review states; production runtime wiring incomplete |
| Double-entry ledger | Unit-tested | Balance/idempotency/concurrency tests |
| PostgreSQL ledger | Integration-tested schema | PostgreSQL 16 migration job and local smoke test; repository concurrency integration coverage remains incomplete |
| Kafka event adapter | Broker-smoke-tested in CI | Pinned Redpanda service proves topic creation and produce/consume; adapter duplicate and outbox recovery behaviours remain deterministic integration tests |
| Transactional outbox | Prototype | In-memory outbox exists; production relay evidence absent |
| Voice verification | Packaged prototype | Authenticated HTTP service, single-use transaction-bound challenges, release image and 90%+ test coverage; biometric/device evaluation not operationally proven |
| Mobile secure storage | Implemented abstraction | Physical-device QA pending |
| AWS infrastructure | Planned/configured | Terraform and static validators; deployment evidence absent |
| Disaster recovery | Documented/validated as policy | No drill report |
| Reconciliation | Implemented prototype | Stuck-payment scan, uncertain-result states and manual review are tested; live provider settlement-file integration remains unproven |
| PCI DSS | Not assessed | No completed scope assessment |
| POPIA/FICA controls | Partial documentation/domain logic | Legal/compliance validation not evidenced |
| Production readiness | Not achieved | Signing/SBOM workflows exist, but no production release, live SLO evidence, independent biometric evaluation or recovery drill |

Terms such as “production”, “durable” and “ready” in legacy phase documents describe design targets or validator inputs unless accompanied by deployment evidence.

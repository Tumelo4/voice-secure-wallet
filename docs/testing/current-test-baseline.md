# Current test baseline

Baseline captured 2026-07-13.

| Area | Current evidence | Result in this workspace | Limitation |
|---|---|---|---|
| Mobile | TypeScript type-check and Node interaction/domain tests | 49 tests passed | Not React Native Testing Library/device QA |
| Java modules | Main-method unit/acceptance-style suites | Sources compile with `javac -Xlint:all`; non-socket suites passed | Maven unavailable locally; no coverage/mutation report |
| Java HTTP listener | Local socket test | Not runnable in restricted sandbox | Socket bind denied by environment |
| Voice | pytest suite | Not run locally | pytest is not installed in workspace runtime |
| PostgreSQL | Migration/schema tests and optional shell smoke test | Static/schema tests passed | No Testcontainers evidence in CI |
| Kafka | In-memory/adapter contract tests | Passed | No live Kafka/Redpanda CI test |
| Terraform | Static baseline tests | Passed | Workflow does not run `terraform validate` |

The GitHub workflow currently runs one combined job containing Maven verification, Python tests/build, mobile validation and whitespace checks. It does not yet provide the independent security, infrastructure, SBOM, signing, performance or resilience gates required by the remediation plan.

No trustworthy line/branch coverage percentage is currently published. Coverage must therefore be reported as **unknown**, not inferred from the number of tests.


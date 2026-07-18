# Current test baseline

Baseline captured 2026-07-18 on revision `80510dac` plus the local payment-recovery changes.

| Area | Current evidence | Result in this workspace | Limitation |
|---|---|---|---|
| Mobile | TypeScript type-check, Node tests and Jest screen tests | 55 Node tests and 2 screen tests passed via `npm run validate` | No physical-device QA |
| Java modules | Pinned Maven 3.9.11 wrapper, upper-bound dependency enforcement, and the full 19-module reactor | Passed with `./mvnw test -Djacoco.skip=true` | Host JVM is newer than CI's Java 17; local JaCoCo instrumentation remains skipped |
| Java HTTP listener | Local Jetty/Javalin socket tests | Passed | Local loopback only |
| Voice | Lockfile-backed pytest suite | 23 tests passed with 93.30% coverage via `uv run --locked --extra test pytest` | Local Python links LibreSSL 2.8.3; CI should use the supported Python/OpenSSL runtime |
| PostgreSQL | Flyway, multi-instance payment start and settlement crash-window tests | Focused Testcontainers suite passed: 4 tests | Disposable PostgreSQL 16, not a managed production database |
| Kafka/Redis | Redpanda delivery, transactional outbox and Redis rate-limit integration | Passed in the full integration attempt | Full integration attempt initially failed on ignored duplicate Flyway files; duplicates were removed and the affected PostgreSQL suite was rerun successfully |
| Terraform | Static baseline and workflow configuration | Not executed in this baseline | Requires a separate Terraform validation run |

Quality, security and container-supply-chain workflows define independent gates, but remain manual-only by explicit ownership decision for this change.

No trustworthy line/branch coverage percentage is currently published. Coverage must therefore be reported as **unknown**, not inferred from the number of tests.

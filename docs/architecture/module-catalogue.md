# Module catalogue

| Reactor module | Classification | Runtime entry point | Data/API/event responsibility | Container/deployment |
|---|---|---:|---|---:|
| `event-core` | Adapter/shared library | No | Event envelopes, publishers and in-memory outbox | No |
| `identity-service` | Domain/application module | No | Identity repository and token/device rules | No |
| `compliance-service` | Domain module | No | Compliance screening and audit concepts | No |
| `ledger-service` | Domain + persistence adapter | No | Ledger, PostgreSQL repository/migrations, ledger events | No |
| `payment-service` | Domain/application module | No | Payment saga, snapshots and migrations | No |
| `notification-service` | Domain/adapter module | No | Notification consumption and repository | No |
| `wallet-service` | Read-model/domain module | No | Customer accounts and balance projection | No |
| `beneficiary-service` | Domain/application module | No | Owned beneficiaries, account verification and cooling-off policy | No |
| `fraud-service` | Domain module | No | Fraud assessment and policy | No |
| `recovery-service` | Domain/application module | No | Account recovery and events | No |
| `support-service` | Domain/application module | No | Support cases and repair workflow | No |
| `ops-service` | Policy validator | No | Infrastructure and operational-plan validation | No |
| `launch-service` | Policy validator | No | Launch evidence validation | No |
| `api-adapter-service` | Application/runtime service | Yes | HTTP listener, authentication context and routes | OCI image defined; deployment not proven |
| `contract-tests` | Test module | No | HTTP/event/schema contracts | N/A |
| `acceptance-tests` | Test module | No | Cross-module acceptance scenarios | N/A |

Outside the reactor, `apps/mobile` is a customer application and `services/voice-service` is a separately packaged Python biometric runtime with an OCI image. Neither has production deployment evidence.

## Immediate architecture decision

Treat all Java business modules as modules in one modular application. Do not infer a network boundary from the `-service` suffix. Renaming/moving modules requires an ADR and boundary tests before mechanical repository restructuring.

# Ubiquitous Language

This glossary keeps the VoiceSecure Wallet code, tests, and PDF plan aligned.
Use these words consistently in commits, PRs, tests, READMEs, and future HTTP or
infrastructure adapters.

## Core Principles

- The ledger is the financial authority.
- Wallet balances are projections of ledger truth.
- Voice is an authentication factor, not the only path to payment completion.
- Compliance blocks are business decisions, not technical failures.
- Compensation failure is a human-in-the-loop incident.

## Bounded Contexts

| Context | Service | Owns | Does Not Own |
| --- | --- | --- | --- |
| Ledger | `ledger-service` | Signed entries, account balances, reconciliation, repair entries. | User-facing balance UI, payment decisions, support case lifecycle. |
| Wallet | `wallet-service` | Wallet metadata and read-model projections from ledger events. | Ledger writes or financial correction. |
| Payment | `payment-service` | Saga state, idempotency, auth/fallback progression, compensation state. | Fraud scoring, biometric matching, ledger internals, notification delivery. |
| Fraud | `fraud-service` | Risk score, auth policy, voice threshold, velocity/device trust decisions. | Regulatory audit ownership and payment state changes. |
| Compliance | `compliance-service` | PEP, sanctions, AML, STR evidence, compliance audit trail. | Fraud scoring formulas and saga orchestration. |
| Identity | `identity-service` | RS256 tokens, refresh-token families, device public keys, device signatures. | Recovery KYC workflow and payment authorization decisions. |
| Voice | `voice-service` | Enrollment, challenge/liveness checks, replay detection, confidence scoring. | OTP delivery and fraud policy selection. |
| Notification | `notification-service` | Receipts, failure notices, compensation notices, OTP fallback delivery. | Payment progression and voice verification decisions. |
| Support | `support-service` | Disputes, freezes, support audit log, repair escalation workflow. | Direct ledger mutation outside the repair port. |
| Recovery | `recovery-service` | ID/video KYC recovery, voice reenrollment request, device certificate reissue. | Primary identity session lifecycle. |
| Operations | `ops-service` | SLO/alert/runbook/release/DR readiness policy. | Runtime alert delivery infrastructure. |
| Launch | `launch-service` | Launch gate evidence and go/no-go validation. | Producing the evidence itself. |
| API Adapter | `api-adapter-service` | HTTP-style request validation, route selection, response shaping, error mapping, runtime auth, trace, rate-limit, request-log guards, and production ingress readiness preflight. | Payment, ledger, wallet, fraud, identity business decisions, or cloud resource provisioning. |

`event-core` is shared infrastructure for domain events and outbox behavior. It
is not a bounded context with business ownership.

`apps/mobile` is the React Native readiness dashboard. It is a presentation
surface over build evidence, not a bounded context that owns payment, ledger,
risk, or launch decisions. Its API access remains behind client, transport, and
token-session, Redux-flow, and resilience-policy ports so mobile screens do not
own HTTP, auth, retry, or offline queue policy.

`api-adapter-service` is a boundary adapter. It protects domain services from
HTTP, JSON, authentication, traceability, rate-limit, and request-log details,
but it does not own business policy. Its local HTTP listener translates sockets
into the same `ApiRequest`/`ApiResponse` runtime port. Its production ingress
validator models the preflight policy for edge TLS, mTLS, external auth,
distributed rate limits, and route exposure; actual load balancers,
certificates, DNS, Kafka, and AWS remain separate infrastructure concerns.

## Terms

| Term | Meaning |
| --- | --- |
| Signed ledger entry | A ledger entry with `signedAmount`; negative values debit, positive values credit. |
| Reconciliation | The proof that all signed ledger entries sum to zero. |
| Repair | A compensating ledger action with justification, audit trail, and support/SRE ownership. |
| Wallet projection | A read model built from ledger events for user-facing balance display. |
| Payment saga | The explicit lifecycle that moves a payment through fraud, auth, funds movement, completion, and compensation. |
| Auth policy | Fraud-selected authentication requirement: `VOICE_ONLY`, `VOICE_OTP`, or `DEVICE_PIN`. |
| Voice fallback | The path from voice timeout/rejection into OTP/PIN instead of blocking a legitimate customer. |
| Compliance hit | A PEP, sanctions, or AML result that blocks payment before funds move. |
| Fraud scored event | Contract event emitted by the fraud context with score, approval, auth policy, voice threshold, and device/velocity evidence. |
| Compliance hit event | Contract event emitted only for blocking PEP, sanctions, or AML screening results. |
| Contract compatibility plan | Release evidence for Pact broker publication, consumer verification, Schema Registry registration, schema ID pinning, and schema compatibility for domain events. |
| Device binding | A registered device key used to sign critical requests. |
| Token family revocation | Revocation of all refresh tokens in a family after reuse is detected. |
| Launch evidence | Measured proof for reconciliation, chaos, security, shadow mode, performance, DR, and documentation gates. |
| Production cutover plan | Launch-owned proof that a production release has change approval, tested rollback, locked feature flags, armed monitoring, on-call coverage, support briefing, and rollback timing evidence. |
| Readiness dashboard | UI surface that summarizes service slices, tests, phase status, and remaining production blockers. |
| Mobile UI stack | React Native TypeScript app surface styled with NativeWind/Tailwind CSS and backed by Redux Toolkit state. |
| Mobile API client | TypeScript boundary that sends API runtime headers, maps payment and wallet responses, preserves API errors, and feeds Redux-friendly request state. |
| Mobile fetch transport | React Native `fetch` adapter that implements the mobile `ApiTransport` port and maps network failures into stable API client errors. |
| Access token provider | Mobile auth port that supplies the bearer token per request so secure storage or refresh logic can change without touching API client methods. |
| Token session | Mobile auth state containing user ID, access token, refresh token, and access-token expiry. |
| Token vault | Mobile persistence port for saving, loading, and clearing token sessions without coupling app code to a specific secure-storage library. |
| Native secure token store | Mobile adapter port for iOS Keychain, Android Keystore, or equivalent secure storage, requiring encrypted, hardware-backed, device-only, biometric/passcode-protected token storage without cloud sync. |
| Secure-store readiness | Executable mobile auth check that blocks unsafe token storage configuration before a production build. |
| Refresh window | Safety interval before access-token expiry where the mobile app refreshes credentials rather than sending a token that may expire in flight. |
| Mobile API flow | Redux thunk-style command that dispatches request-started, request-succeeded, or request-failed actions around wallet and payment API client calls. |
| Mobile command form | React Native screen-level form state that validates wallet-balance or payment-start input before dispatching a mobile API flow. |
| Request state | Redux-friendly `idle`, `loading`, `succeeded`, or `failed` state with optional data, error, and trace ID evidence. |
| Mobile resilience policy | Local mobile rule set for retry backoff, non-retryable failures, offline payment queueing, and ordered replay before durable infrastructure exists. |
| Offline payment queue | Device-local queue of payment-start commands keyed by idempotency key so duplicate taps or reconnects do not create duplicate payment commands. |
| API adapter | Boundary layer that translates HTTP-style requests into domain service calls and maps domain outcomes back to stable JSON responses. |
| API runtime boundary | Guard layer that verifies bearer tokens, requires trace IDs, enforces route-scoped authorization, rate-limits authenticated principals, forwards valid requests, and records request outcomes. |
| API local HTTP listener | JDK HTTP server adapter that maps localhost socket requests into `ApiRuntime` and preserves status, JSON headers, and retry hints. |
| API production ingress readiness | Executable preflight validation for TLS 1.3, mTLS, client certificate forwarding, OIDC/JWKS, distributed rate limits, WAF, HSTS, trace forwarding, body limits, and public route exposure. |
| Durable infrastructure readiness | Executable preflight validation for Kafka topic durability and AWS high-availability/encryption controls before live provisioning. |
| Kafka topic spec | Required topic shape including partitions, replication factor, schema compatibility, dead-letter queue, and retention settings. |
| AWS infrastructure spec | Required cloud shape covering region, private subnets, KMS, MSK TLS/IAM, RDS HA/PITR/deletion protection, Redis encryption, S3 object lock, and managed secret references. |
| Terraform AWS baseline | Infrastructure-as-code module declaring the first AWS network, encryption, broker, database, cache, audit bucket, and secret-reference resources without applying them. |

## Testing Style

| Style | How We Use It |
| --- | --- |
| TDD | Add failing tests first for new behavior, then implement the smallest green slice, then refactor. |
| BDD | Write cross-context scenarios in product language using `Given/When/Then` style test names. |
| DDD | Keep each service responsible for its bounded context and connect contexts through ports, events, or explicit adapters. |

## Current BDD Scenarios

- Scenario: voice fallback keeps a legitimate payment moving.
- Scenario: compliance hit blocks funds movement.
- Scenario: wallet read model follows ledger truth.

These scenarios live in
`tests/acceptance-tests/src/test/java/com/voicesecure/acceptance`.

## Current Contract Tests

- Contract: `fraud.scored` carries auth policy and threshold.
- Contract: `compliance.hit` carries PEP hit evidence.
- Contract: clear compliance results cannot publish `compliance.hit`.
- Compatibility: Pact and Schema Registry readiness blocks missing broker,
  consumer verification, schema registration, schema ID pinning, or
  non-`BACKWARD_TRANSITIVE` compatibility.

These local contract tests live in
`tests/contract-tests/src/test/java/com/voicesecure/contracts`. They are now
the domain-level stepping stone plus the credential-free compatibility gate
before live Pact broker and Schema Registry credentials are wired into CI.

# Architecture and UML diagrams

Status: **as implemented in the repository**. VoiceSecure Wallet is an
engineering prototype, not an operationally validated production system.

These views deliberately distinguish deployable processes from Java modules.
The Java `*-service` modules are bounded contexts composed into one API
application; the Python voice runtime is a separate process.

## 1. System and deployment architecture

This is the primary architecture view for design reviews, threat modelling,
operations, and dependency ownership.

```mermaid
flowchart LR
    Customer["Customer"]
    Operator["Support / finance operator"]
    Mobile["Mobile app<br/>Expo / React Native"]

    subgraph Trust["AWS trust boundary (target deployment)"]
        Edge["TLS ingress / WAF"]

        subgraph Java["Java modular application"]
            API["HTTP API + runtime guards<br/>OIDC, scopes, rate limits, trace logging"]
            Identity["Identity"]
            Payments["Payments"]
            Ledger["Ledger"]
            Wallet["Wallet projection"]
            Risk["Fraud + compliance"]
            Support["Support + recovery"]
            Notify["Notifications"]
            Outbox["Transactional outbox relay"]

            API --> Identity
            API --> Payments
            API --> Wallet
            API --> Support
            Payments --> Risk
            Payments --> Ledger
            Ledger --> Wallet
            Payments --> Outbox
            Ledger --> Outbox
        end

        Voice["Python voice service<br/>liveness + verification"]
        DB[("PostgreSQL<br/>sagas, ledger, projections, outbox")]
        Kafka[("Kafka / MSK<br/>domain events + DLQs")]
        Redis[("Redis<br/>distributed rate limits")]
        Audit[("S3 / KMS<br/>audit evidence")]

        Edge --> API
        API --> Redis
        Java --> DB
        Outbox --> Kafka
        API --> Voice
        Kafka --> Notify
        Kafka -. audit consumers .-> Audit
    end

    IdP["OIDC / JWKS provider"]
    Directory["Beneficiary account directory"]

    Customer --> Mobile
    Mobile --> Edge
    Operator --> Edge
    API --> IdP
    API --> Directory
```

Key boundaries:

- The ledger is the source of financial truth; wallet balances are projections.
- The API derives customer identity from verified tokens. Request bodies are not
  trusted as an ownership source.
- Voice is an authorisation signal, never a balance or settlement authority.
- PostgreSQL transactions plus an outbox separate durable state change from
  asynchronous Kafka publication.
- `ops-service` and `launch-service` are policy/readiness validators, not
  production request-serving processes.

## 2. Java module dependency architecture

This view is useful for code ownership and architecture-boundary reviews.

```mermaid
flowchart TB
    App["api-adapter-service<br/>composition root + HTTP adapters"]
    Payment["payment-service<br/>saga aggregate"]
    Ledger["ledger-service<br/>double-entry ledger"]
    Wallet["wallet-service<br/>read projection"]
    Identity["identity-service"]
    Fraud["fraud-service"]
    Compliance["compliance-service"]
    Beneficiary["beneficiary-service"]
    Support["support-service"]
    Recovery["recovery-service"]
    Notification["notification-service"]
    Events["event-core<br/>envelopes, outbox, Kafka ports"]
    Ops["ops-service / launch-service<br/>build-time validation"]

    App --> Payment
    App --> Ledger
    App --> Wallet
    App --> Identity
    App --> Fraud
    App --> Beneficiary
    App --> Support
    Payment --> Events
    Ledger --> Events
    Wallet --> Events
    Compliance --> Events
    Recovery --> Events
    Notification --> Events
    Fraud --> Compliance
    Ops -. validates .-> App
```

The dependency direction should remain inward toward domain modules and ports.
Infrastructure adapters and runtime assembly belong at the application edge.

## 3. UML class diagram — payment settlement core

This is the highest-value static UML view because payment orchestration is the
system's consistency boundary.

```mermaid
classDiagram
    direction LR

    class PaymentSaga {
        -UUID sagaId
        -UUID idempotencyKey
        -PaymentSagaState state
        -long version
        +initiate(PaymentRequest) PaymentSaga
        +approveFraud(FraudDecision)
        +recordVoiceOutcome(VoiceOutcome)
        +fundsReserved()
        +ledgerCommitStarted()
        +ledgerCommitSucceeded()
        +complete()
        +compensationSucceeded()
        +isTerminal() boolean
    }

    class PaymentSagaService {
        -PaymentSagaRepository repository
        +start(PaymentRequest, FraudDecision) PaymentSaga
        +recordVoiceOutcome(UUID, VoiceOutcome) PaymentSaga
        +markFundsReserved(UUID) PaymentSaga
        +startLedgerCommit(UUID) PaymentSaga
        +completeLedgerCommit(UUID) PaymentSaga
        +complete(UUID) PaymentSaga
    }

    class PaymentSagaRepository {
        <<interface>>
        +findBySagaId(UUID) Optional~PaymentSaga~
        +findByIdempotencyKey(UUID) Optional~PaymentSaga~
        +createIfAbsent(PaymentSaga) PaymentSaga
        +save(PaymentSaga)
    }

    class PostgresPaymentSagaRepository
    class PaymentSettlementHandler {
        <<interface>>
        +settle(PaymentSaga) PaymentSaga
    }
    class PaymentRecoveryAction {
        <<interface>>
        +recover(PaymentSaga) PaymentSaga
    }
    class PaymentSettlementCoordinator {
        -PaymentSagaService payments
        -LedgerService ledger
        +settle(PaymentSaga) PaymentSaga
        +recover(PaymentSaga) PaymentSaga
    }
    class LedgerService {
        +reserveFunds(UUID, UUID, UUID, long, String, Duration)
        +commitReservedTransfer(UUID, UUID, UUID, UUID, UUID, long, String)
        +releaseFunds(UUID)
    }
    class PaymentProductionRuntime {
        -OutboxRelayWorker relayWorker
        -PaymentRecoveryWorker recoveryWorker
        +paymentService() PaymentSagaService
        +close()
    }

    PaymentSagaService --> PaymentSagaRepository
    PaymentSagaService --> PaymentSaga
    PostgresPaymentSagaRepository ..|> PaymentSagaRepository
    PaymentSettlementCoordinator ..|> PaymentSettlementHandler
    PaymentSettlementCoordinator ..|> PaymentRecoveryAction
    PaymentSettlementCoordinator --> PaymentSagaService
    PaymentSettlementCoordinator --> LedgerService
    PaymentProductionRuntime --> PaymentSagaService
    PaymentProductionRuntime --> PaymentSettlementCoordinator
```

## 4. UML state machine — durable payment saga

This view captures the legal lifecycle, terminal outcomes, compensation, and
operator-driven recovery states.

```mermaid
stateDiagram-v2
    [*] --> INITIATED
    INITIATED --> FRAUD_CHECK_PENDING
    FRAUD_CHECK_PENDING --> FRAUD_REJECTED: rejected
    FRAUD_CHECK_PENDING --> VOICE_VERIFICATION_PENDING: approved

    VOICE_VERIFICATION_PENDING --> FUNDS_RESERVING: voice approved
    VOICE_VERIFICATION_PENDING --> VOICE_REJECTED: rejected / spoof
    VOICE_VERIFICATION_PENDING --> VOICE_VERIFICATION_TIMEOUT: timeout
    VOICE_VERIFICATION_PENDING --> VOICE_FALLBACK_PENDING: fallback required
    VOICE_FALLBACK_PENDING --> FUNDS_RESERVING: verified
    VOICE_FALLBACK_PENDING --> VOICE_FALLBACK_FAILED: failed

    FUNDS_RESERVING --> FUNDS_RESERVED: reservation succeeds
    FUNDS_RESERVING --> FUNDS_RESERVATION_FAILED: reservation fails
    FUNDS_RESERVED --> LEDGER_COMMITTING
    LEDGER_COMMITTING --> LEDGER_COMMITTED: balanced posting succeeds
    LEDGER_COMMITTING --> LEDGER_COMMIT_FAILED: posting fails
    LEDGER_COMMITTING --> UNKNOWN_EXTERNAL_STATUS: outcome indeterminate
    LEDGER_COMMITTED --> COMPLETING
    COMPLETING --> COMPLETED

    LEDGER_COMMIT_FAILED --> COMPENSATION_IN_PROGRESS
    COMPENSATION_IN_PROGRESS --> COMPENSATED: funds released
    COMPENSATION_IN_PROGRESS --> COMPENSATION_FAILED: release fails

    UNKNOWN_EXTERNAL_STATUS --> RECONCILIATION_REQUIRED
    RECONCILIATION_REQUIRED --> LEDGER_COMMITTED: provider confirms success
    RECONCILIATION_REQUIRED --> COMPENSATION_IN_PROGRESS: provider confirms failure
    RECONCILIATION_REQUIRED --> MANUAL_REVIEW: cannot decide safely

    FRAUD_REJECTED --> [*]
    VOICE_REJECTED --> [*]
    VOICE_VERIFICATION_TIMEOUT --> [*]
    VOICE_FALLBACK_FAILED --> [*]
    FUNDS_RESERVATION_FAILED --> [*]
    COMPLETED --> [*]
    COMPENSATED --> [*]
    COMPENSATION_FAILED --> [*]
```

## 5. UML sequence — authorised payment with outbox delivery

This view highlights synchronous consistency decisions and the asynchronous
event boundary.

```mermaid
sequenceDiagram
    autonumber
    actor Customer
    participant Mobile
    participant API as API runtime
    participant IdP as OIDC / JWKS
    participant Fraud
    participant Voice
    participant Saga as Payment saga
    participant Ledger
    participant DB as PostgreSQL
    participant Relay as Outbox relay
    participant Kafka
    participant Consumer as Notification / projection

    Customer->>Mobile: Submit payment
    Mobile->>API: POST /v1/payments<br/>Bearer + trace + idempotency key
    API->>IdP: Verify token / resolve keys
    IdP-->>API: Trusted claims and scopes
    API->>Fraud: Assess amount, identity, velocity, compliance
    Fraud-->>API: Approved + voice policy
    API->>Saga: start(request, decision)
    Saga->>DB: createIfAbsent(idempotency key)
    DB-->>Saga: Durable saga
    Saga-->>API: VOICE_VERIFICATION_PENDING
    API-->>Mobile: Payment reference + challenge state

    Mobile->>API: Submit bound voice verification
    API->>Voice: Verify challenge, liveness, replay, match
    Voice-->>API: Approved outcome
    API->>Saga: recordVoiceOutcome()
    Saga->>DB: Persist FUNDS_RESERVING

    API->>Ledger: reserveFunds()
    Ledger->>DB: Durable reservation
    API->>Ledger: commitReservedTransfer()
    Ledger->>DB: Atomic balanced entries + outbox event
    DB-->>Ledger: Commit
    API->>Saga: completeLedgerCommit(); complete()
    Saga->>DB: Persist COMPLETED + payment outbox event
    API-->>Mobile: Completed receipt

    loop Poll pending outbox rows
        Relay->>DB: Claim pending events
        Relay->>Kafka: Publish keyed envelope
        Kafka-->>Relay: Acknowledge
        Relay->>DB: Mark published
    end
    Kafka-->>Consumer: Domain event
    Consumer->>Consumer: Idempotent handling by event ID
```

## Review rules

- Changes to deployment boundaries must update the system/deployment view and
  ADR-001.
- Changes to `PaymentSagaState` or transition methods must update the state
  machine and payment contract tests.
- New module dependencies must preserve the inward dependency direction and
  pass `quality/architecture-tests`.
- New asynchronous side effects must originate from durable outbox records and
  document retry, ordering, idempotency, and dead-letter behavior.

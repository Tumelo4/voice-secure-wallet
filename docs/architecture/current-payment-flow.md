# Current payment flow

```mermaid
sequenceDiagram
    actor Customer
    participant Mobile
    participant API as Java API runtime
    participant Wallet
    participant Payment as Payment saga module
    participant Ledger
    participant Fraud

    Customer->>Mobile: Select account and recipient, enter decimal amount/reference
    Mobile->>API: POST /v1/payments + bearer token + hidden idempotency key
    API->>API: Validate token and derive customer identity
    API->>Wallet: Verify source-account ownership
    API->>Wallet: Check destination account exists
    API->>Fraud: Assess payment request
    API->>Payment: Atomically create/load saga by idempotency key
    Payment-->>API: Voice verification pending
    API-->>Mobile: Customer-safe reference and status
    Mobile-->>Customer: Show verification state
    API->>Ledger: Reserve funds, then commit balanced transfer
    Ledger-->>Payment: Durable idempotent outcome
    Payment-->>API: Complete or compensate
```

## Known gaps

- Mobile still needs physical-device validation for microphone, codecs,
  accessibility, secure storage and unreliable networks.
- Provider-specific reconciliation adapters and real settlement-file ingestion
  remain deployment integrations.
- The production recovery worker now resumes deterministic internal settlement
  states and routes unknown external outcomes to reconciliation; deployed
  multi-instance soak evidence is still required.
- Customer review, authorisation and receipt journeys need final device-level QA.

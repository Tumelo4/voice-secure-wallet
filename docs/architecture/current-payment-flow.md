# Current payment flow

```mermaid
sequenceDiagram
    actor Customer
    participant Mobile
    participant API as Java API runtime
    participant Wallet
    participant Payment as Payment saga module
    participant Fraud

    Customer->>Mobile: Select account and recipient, enter decimal amount/reference
    Mobile->>API: POST /v1/payments + bearer token + hidden idempotency key
    API->>API: Validate token and derive customer identity
    API->>Wallet: Verify source-account ownership
    API->>Wallet: Check destination account exists
    API->>Fraud: Assess payment request
    API->>Payment: Start server-identified saga
    Payment-->>API: Voice verification pending
    API-->>Mobile: Customer-safe reference and status
    Mobile-->>Customer: Show verification state
```

## Known gaps

- Accounts and recipients are currently seeded UI choices rather than loaded through `/v1/me` resources.
- A beneficiary aggregate and access policy are not yet implemented.
- The public request is not yet the target nested `Money` contract.
- Review, explicit authorisation, durable recovery and receipt screens are incomplete.
- Saga persistence, outbox and provider reconciliation exist only in partial/prototype form.

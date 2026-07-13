# Current container diagram

This diagram describes checked-in runtime reality, not the names of Maven modules.

```mermaid
flowchart TB
    subgraph Device[Customer device]
      Mobile[Mobile app\nTypeScript / Expo / React Native]
      Vault[Secure token-store abstraction]
      Mobile --> Vault
    end

    subgraph Java[Java application deployment]
      HTTP[API adapter and HTTP runtime]
      Identity[Identity module]
      Payments[Payments and fraud modules]
      Ledger[Ledger and wallet modules]
      Other[Compliance, notifications, recovery and support modules]
      Events[Event-core adapters and in-memory outbox]
      HTTP --> Identity
      HTTP --> Payments
      Payments --> Ledger
      Payments --> Other
      Payments --> Events
    end

    Voice[Voice service prototype\nPython] 
    Mobile -->|HTTPS-shaped contract| HTTP
    Payments -->|voice port; integration incomplete| Voice
    Events -.->|adapter exists; live integration unproven| Kafka[(Kafka)]
    Ledger -.->|migration/repository exists; CI integration incomplete| DB[(PostgreSQL)]
```

Only the Java API adapter exposes a runtime entry point. The other Java reactor modules are libraries composed into that runtime. `ops-service` and `launch-service` are build-time policy validators. The Python voice module is a separately packaged prototype but has no checked-in deployment definition.


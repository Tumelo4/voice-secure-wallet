# Test strategy

Unit and property tests protect money, transitions, policies and ledger invariants. PostgreSQL and broker Testcontainers cover migrations, locks, outbox recovery, duplicates and ordering. Contract tests validate OpenAPI/event compatibility. Mobile interaction tests cover selection, review, fallback, double taps, offline/token failures and accessibility. Critical contracts/transitions require complete scenario coverage; domain target is 90%, application 80%, and money/ledger mutation score 80%.


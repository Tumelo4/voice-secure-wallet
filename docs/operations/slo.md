# Service-level objectives

| Journey | SLI | Target |
|---|---|---|
| Payment initiation | Successful safe responses / valid requests | 99.9% monthly |
| Ledger posting | Correct durable postings | 99.99%; correctness errors zero tolerance |
| Account reads | Successful latency | 99.9%, p95 < 500 ms |
| Voice decision | Decision latency | 99%, p95 < 3 s; fallback available |
| Reconciliation | Unknown outcomes resolved | 99% within 24 h |

Error budgets never permit ledger imbalance, duplicate posting, unauthorised access or lost audit evidence.


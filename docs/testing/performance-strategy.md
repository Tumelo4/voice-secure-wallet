# Performance strategy

Measure concurrent debits, balance reads, idempotency lookup, outbox recovery, initiation latency and ledger lock contention with production-shaped data. Record versioned reports and thresholds in CI artifacts. Performance tests must verify correctness and reconciliation totals, not throughput alone.

## Executable local baseline

The payment load probe creates 10,000 unique saga starts, records p95 initiation
latency and throughput, verifies that every accepted saga remains readable, and
replays one request to confirm idempotency. It fails if p95 reaches 5 ms,
throughput falls below 1,000 operations/second, or a correctness check fails.

```sh
./mvnw -pl services/payment-service -am test-compile
java -cp services/payment-service/target/classes:services/payment-service/target/test-classes \
  com.voicesecure.payments.PaymentLoadProbe \
  services/payment-service/target/performance/payment-load.json
```

The JSON report is uploaded by the existing performance/resilience CI job. This
in-memory probe is a regression baseline, not evidence that the production SLO
is met. Production-shaped database, broker, and concurrency tests remain part
of the managed-environment launch gate.

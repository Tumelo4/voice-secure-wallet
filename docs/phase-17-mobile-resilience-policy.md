# Phase 17 Mobile Resilience Policy

This slice adds local-only mobile resilience before Kafka or AWS infrastructure
is introduced:

- retryable transport and runtime failures use capped exponential backoff;
- auth, validation, and exhausted attempts do not retry;
- offline payment commands enqueue idempotently by idempotency key;
- local offline queue depth is capped to protect device memory;
- queued payment commands drain in order;
- retryable drain failures keep the blocking command queued and stop later
  commands from overtaking it.

This policy is intentionally device-local. It improves mobile behavior while
the product is still before durable event streaming, cloud queues, or AWS-backed
storage.

## SOLID Notes

- **Single Responsibility:** retry decisions and offline queue behavior live in
  a policy module, not inside components or API clients.
- **Open/Closed:** later native persistence, telemetry, or retry schedulers can
  wrap the same policy without changing the mobile API client.
- **Liskov Substitution:** tests and production clients only need the
  `startPayment` port for queue drain.
- **Interface Segregation:** offline drain depends on the payment command port,
  not the whole API client surface.
- **Dependency Inversion:** the policy depends on local interfaces and typed
  request errors, not on Kafka, AWS, or a concrete network stack.

## TDD Notes

- **Red:** resilience tests first referenced a missing retry/offline queue
  policy module.
- **Green:** capped backoff, non-retryable failure decisions, idempotent enqueue,
  queue-depth protection, ordered drain, and retryable blocker preservation made
  the tests pass.
- **Refactor:** readiness evidence, README benchmark, release runbook, and
  ubiquitous language now document the local resilience boundary.

## Kafka/AWS Boundary

The next local phase can still add a production network listener. After that,
durable outbox/event streaming, cloud queues, distributed rate limits, managed
databases, and deployment infrastructure should wait for Kafka/AWS-capable
integration phases.

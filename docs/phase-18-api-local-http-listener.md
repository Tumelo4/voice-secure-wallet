# Phase 18 API Local HTTP Listener

This slice provides a production-capable embedded transport without crossing
into cloud infrastructure:

- `ApiHttpServer` wraps the existing `ApiEndpoint` port with Javalin/Jetty;
- socket request method, path, headers, and body map into `ApiRequest`;
- `ApiRuntime` explicitly implements `ApiEndpoint`, so the same auth, trace,
  rate-limit, routing, and request-log guards run behind the listener;
- `ApiResponse` status, JSON headers, body, and `Retry-After` hints are copied
  back to the HTTP response;
- tests verify wallet GET, payment POST JSON, and rate-limit response headers
  through a real localhost socket.

## SOLID Notes

- **Single Responsibility:** the listener translates sockets; runtime guards
  still own auth, trace, rate limit, and logging decisions.
- **Open/Closed:** future TLS, mTLS, external auth, or reverse-proxy adapters can
  wrap the same runtime without changing payment and wallet adapters.
- **Liskov Substitution:** `ApiRuntime`, routers, and test endpoints can all be
  passed through the same `ApiEndpoint` port.
- **Interface Segregation:** the listener depends only on `ApiEndpoint`, not on
  payment, wallet, fraud, or ledger services directly.
- **Dependency Inversion:** network I/O depends on domain-facing ports rather
  than forcing domain services to depend on HTTP server APIs.

## TDD Notes

- **Red:** listener tests first referenced a missing `ApiHttpServer` class.
- **Green:** the Javalin/Jetty server adapter and explicit runtime endpoint contract
  made real socket wallet, payment, and rate-limit tests pass.
- **Refactor:** README, release runbook, ubiquitous language, and readiness
  evidence now distinguish local listener work from production infrastructure.

## Kafka/AWS Boundary

This phase is the last safe local network step before infrastructure-heavy
work. Durable outbox adapters, Kafka topics, distributed rate limiting,
managed databases, mTLS ingress, and AWS deployment should be handled in the
Kafka/AWS-capable phases rather than simulated in local-only code.

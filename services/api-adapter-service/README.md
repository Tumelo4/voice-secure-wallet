# api-adapter-service

Java 17 framework-free API adapter, runtime boundary contracts, and local JDK
HTTP listener for VoiceSecure Wallet.

## Problem Statement

Domain services can be correct and still be unsafe to expose if the HTTP
boundary leaks exceptions, ignores idempotency, or lets controllers own business
rules. That creates brittle integrations, inconsistent error handling, and
payment retries that are hard to reason about.

## Impact

- Users and clients get stable JSON responses for payment commands and wallet
  balance reads.
- The domain services stay framework-independent and easier to test.
- Future production deployment work can focus on certificate provisioning, load
  balancer rollout, DNS, and external auth provider integration without
  changing payment or wallet behavior.

## Scope

This service models the API adapter, runtime boundary, local JDK HTTP listener,
and production ingress readiness preflight. It owns request normalization, route
selection, JSON response shaping, runtime guards, socket-to-request translation,
production ingress policy validation, and error mapping for the first two
production-facing routes:

- `POST /payments`
- `GET /wallets/{accountId}/balance`

`PaymentApiAdapter` depends on `PaymentSagaService` and a
`FraudDecisionProvider` port. `WalletApiAdapter` depends on `WalletService`.
`ApiRouter` depends on the `ApiEndpoint` abstraction so more routes can be
added without rewriting router behavior. `ApiRuntime` depends on ports for
bearer-token verification, route-scoped authorization, rate limiting, and
request logging so production adapters can replace the in-memory
implementations later. `ApiHttpServer` depends on the same `ApiEndpoint` port,
so the listener remains an adapter rather than a business-policy owner.
`ProductionIngressValidator` checks deployment evidence for edge TLS, mTLS,
external auth, distributed rate limits, WAF, HSTS, trace forwarding, body-size
limits, and public health paths without provisioning any cloud resources.

## Current Guarantees

- Payment POST requires `Idempotency-Key` and `X-Trace-Id` headers.
- Payment POST maps domain idempotency conflicts to HTTP `409`.
- Payment validation failures return JSON `400` responses without leaking Java
  exception names.
- Wallet balance reads return JSON `200` responses from the wallet projection.
- Unknown routes return JSON `404` responses.
- Runtime requests require `Authorization: Bearer ...` and `X-Trace-Id`.
- Invalid bearer tokens return JSON `403` responses.
- Protected routes require route-scoped bearer tokens.
- Per-principal rate-limit failures return JSON `429` with `Retry-After`.
- Runtime outcomes are recorded with principal, trace, method, path, and status.
- Local JDK HTTP listener forwards socket requests through the same runtime
  guards and preserves response status, JSON headers, and retry hints.
- Production ingress readiness requires TLS 1.3, mTLS, forwarded client
  certificate identity, OIDC/JWKS configuration, distributed rate-limit storage,
  WAF, HSTS, trace forwarding, a 256 KB request body cap, health-only public
  paths, and no public admin routes.

## Benchmark

- 5 API adapter tests, 6 API runtime tests, 2 identity bearer verifier tests,
  3 local HTTP listener tests, and 3 production ingress readiness tests pass
  through the same direct Java compile/test loop used by CI.
- The adapter tests prove route behavior, JSON response shape, error codes, and
  SOLID routing boundaries.
- The runtime tests prove auth, route-scoped authorization, trace,
  rate-limit, forwarding, and audit-log behavior.
- The listener tests prove wallet GET, payment POST, real socket routing, JSON
  response headers, request logging, and `Retry-After` propagation.
- The production ingress tests prove transport security, runtime controls, and
  public route exposure are blocked before production.

## How To Use It

```java
PaymentSagaService payments = new PaymentSagaService(new InMemoryPaymentSagaRepository());
WalletService wallets = new WalletService(new InMemoryWalletRepository());

ApiRouter router = new ApiRouter(
        new PaymentApiAdapter(payments, request -> new FraudDecision(0.18, AuthPolicy.VOICE_OTP, true, "")),
        new WalletApiAdapter(wallets)
);

ApiResponse response = router.handle(new ApiRequest(
        "POST",
        "/payments",
        Map.of("Idempotency-Key", idempotencyKey.toString(), "X-Trace-Id", "trace-api-1"),
        paymentJson
));

ApiRuntime runtime = new ApiRuntime(
        router,
        StaticBearerTokenVerifier.of(Map.of(
                "token-user-1", ApiPrincipal.of("user-1", "wallet:payment", "wallet:balance")
        )),
        new InMemoryApiRateLimiter(100),
        new InMemoryApiRequestLogSink()
);

ApiResponse guardedResponse = runtime.handle(new ApiRequest(
        "POST",
        "/payments",
        Map.of(
                "Authorization", "Bearer token-user-1",
                "Idempotency-Key", idempotencyKey.toString(),
                "X-Trace-Id", "trace-api-1"
        ),
        paymentJson
));

try (ApiHttpServer server = ApiHttpServer.start(runtime)) {
    // server.uri("/wallets/{accountId}/balance") returns a localhost URL for
    // local smoke tests or development clients.
}
```

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

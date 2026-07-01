# api-adapter-service

Java 17 framework-free API adapter and runtime boundary contracts for
VoiceSecure Wallet.

## Problem Statement

Domain services can be correct and still be unsafe to expose if the HTTP
boundary leaks exceptions, ignores idempotency, or lets controllers own business
rules. That creates brittle integrations, inconsistent error handling, and
payment retries that are hard to reason about.

## Impact

- Users and clients get stable JSON responses for payment commands and wallet
  balance reads.
- The domain services stay framework-independent and easier to test.
- Future network server work can focus on mTLS, deployment, and external auth
  provider integration without changing payment or wallet behavior.

## Scope

This service models the API adapter and runtime boundary without starting a
network server. It owns request normalization, route selection, JSON response
shaping, runtime guards, and error mapping for the first two production-facing
routes:

- `POST /payments`
- `GET /wallets/{accountId}/balance`

`PaymentApiAdapter` depends on `PaymentSagaService` and a
`FraudDecisionProvider` port. `WalletApiAdapter` depends on `WalletService`.
`ApiRouter` depends on the `ApiEndpoint` abstraction so more routes can be added
without rewriting router behavior. `ApiRuntime` depends on ports for bearer
token verification, rate limiting, and request logging so production adapters
can replace the in-memory implementations later.

## Current Guarantees

- Payment POST requires `Idempotency-Key` and `X-Trace-Id` headers.
- Payment POST maps domain idempotency conflicts to HTTP `409`.
- Payment validation failures return JSON `400` responses without leaking Java
  exception names.
- Wallet balance reads return JSON `200` responses from the wallet projection.
- Unknown routes return JSON `404` responses.
- Runtime requests require `Authorization: Bearer ...` and `X-Trace-Id`.
- Invalid bearer tokens return JSON `403` responses.
- Per-principal rate-limit failures return JSON `429` with `Retry-After`.
- Runtime outcomes are recorded with principal, trace, method, path, and status.

## Benchmark

- 5 API adapter tests and 5 API runtime tests pass through the same direct Java
  compile/test loop used by CI.
- The adapter tests prove route behavior, JSON response shape, error codes, and
  SOLID routing boundaries.
- The runtime tests prove auth, trace, rate-limit, forwarding, and audit-log
  behavior.

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
        StaticBearerTokenVerifier.of(Map.of("token-user-1", "user-1")),
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
```

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

# api-adapter-service

Java 17 framework-free API adapter contracts for VoiceSecure Wallet.

## Problem Statement

Domain services can be correct and still be unsafe to expose if the HTTP
boundary leaks exceptions, ignores idempotency, or lets controllers own business
rules. That creates brittle integrations, inconsistent error handling, and
payment retries that are hard to reason about.

## Impact

- Users and clients get stable JSON responses for payment commands and wallet
  balance reads.
- The domain services stay framework-independent and easier to test.
- Future HTTP runtime work can focus on auth, mTLS, rate limits, and
  deployment concerns without changing payment or wallet behavior.

## Scope

This service models the API adapter layer without starting a network server. It
owns request normalization, route selection, JSON response shaping, and error
mapping for the first two production-facing routes:

- `POST /payments`
- `GET /wallets/{accountId}/balance`

`PaymentApiAdapter` depends on `PaymentSagaService` and a
`FraudDecisionProvider` port. `WalletApiAdapter` depends on `WalletService`.
`ApiRouter` depends on the `ApiEndpoint` abstraction so more routes can be added
without rewriting router behavior.

## Current Guarantees

- Payment POST requires `Idempotency-Key` and `X-Trace-Id` headers.
- Payment POST maps domain idempotency conflicts to HTTP `409`.
- Payment validation failures return JSON `400` responses without leaking Java
  exception names.
- Wallet balance reads return JSON `200` responses from the wallet projection.
- Unknown routes return JSON `404` responses.

## Benchmark

- 5 API adapter tests pass through the same direct Java compile/test loop used
  by CI.
- The adapter tests prove route behavior, JSON response shape, error codes, and
  SOLID routing boundaries.

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
```

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

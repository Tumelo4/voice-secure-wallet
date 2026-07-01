# Phase 9 API Adapters

This slice adds the first production-facing API boundary described by the build
plan:

- framework-free HTTP request and response contracts;
- `POST /payments` command mapping into `payment-service`;
- `GET /wallets/{accountId}/balance` query mapping into `wallet-service`;
- idempotency-key and trace-header validation;
- JSON error responses for validation, idempotency conflicts, missing wallets,
  and unknown routes;
- router dependency inversion through `ApiEndpoint` so new adapters can be
  added without rewriting routing logic.

The implementation deliberately avoids a web framework for this phase. The goal
is to prove adapter behavior with deterministic tests before choosing the
runtime server, auth middleware, mTLS policy, and rate-limit layer.

## TDD Notes

- **Red:** `ApiAdapterTests` first referenced the missing router, request,
  response, payment adapter, and wallet adapter classes.
- **Green:** the smallest request/response, JSON, payment, wallet, and routing
  implementation made the adapter contract pass.
- **Refactor:** `ApiEndpoint` was introduced so the router depends on an
  abstraction rather than concrete bounded-context adapters.

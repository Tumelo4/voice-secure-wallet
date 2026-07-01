# Phase 10 API Runtime Boundary

This slice adds the first runtime safety layer around the Phase 9 API adapter
contracts:

- bearer-token verification through a `BearerTokenVerifier` port;
- trace-header enforcement before routing;
- per-principal in-memory rate limiting;
- request audit logging with principal, trace, method, path, and response
  status;
- immutable response header extension for runtime headers such as
  `Retry-After`;
- router dependency inversion preserved through the `ApiEndpoint` abstraction.

The implementation still does not start a network listener. It proves the
runtime boundary behavior before adding a production server, auth provider, mTLS
termination, and distributed rate-limit storage.

## TDD Notes

- **Red:** `ApiRuntimeTests` first referenced missing runtime, token verifier,
  rate limiter, audit log, and route-counting test adapter types.
- **Green:** `ApiRuntime` enforced trace, authentication, authorization, rate
  limiting, forwarding, and audit logging through small ports.
- **Refactor:** `ApiResponse.withHeader` centralized immutable response header
  extension, and `ApiRouter` now implements `ApiEndpoint` directly.

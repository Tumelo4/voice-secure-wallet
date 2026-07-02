# Phase 13 Mobile API Client Boundary

This slice connects the React Native TypeScript UI layer to the service API
contract without hardwiring network calls into components:

- typed `VoiceSecureApiClient` for `POST /payments` and
  `GET /wallets/{accountId}/balance`;
- `ApiTransport` port so React Native fetch, mocks, or future offline adapters
  can be swapped in;
- API runtime headers for bearer auth, trace IDs, idempotency, and JSON content;
- typed `ApiClientError` preserving status, code, message, and `Retry-After`;
- Redux-friendly request state helpers for idle, loading, succeeded, and failed
  transitions.

The client still uses deterministic test transports. Production follow-up work
should add a React Native fetch transport, secure token storage, retry/backoff
policy, and screen-level async thunks.

## TDD Notes

- **Red:** mobile API tests first referenced missing client and request-state
  modules.
- **Green:** the transport-based client and request-state model made payment,
  wallet, error, and async-state tests pass.
- **Refactor:** CI now runs every mobile TypeScript test file rather than a
  single dashboard test file.

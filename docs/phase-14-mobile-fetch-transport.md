# Phase 14 Mobile Fetch Transport

This slice turns the mobile API client boundary into a production-ready network
port without putting HTTP details into React Native components:

- `FetchApiTransport` implements the existing `ApiTransport` interface;
- base URLs and request paths join without duplicate slashes;
- request method, headers, and body pass through unchanged;
- response status, headers, and body are mapped back to the API client contract;
- network failures become deterministic `ApiClientError` values with
  `NETWORK_UNAVAILABLE`;
- `AccessTokenProvider` supplies a fresh bearer token for each API request.

The design follows SOLID boundaries:

- **Single Responsibility:** the API client shapes payment and wallet calls,
  while the fetch transport only handles network I/O.
- **Open/Closed:** future retry, offline, or tracing transports can implement
  `ApiTransport` without changing client methods.
- **Liskov Substitution:** tests, React Native fetch, and future adapters use
  the same transport request and response contract.
- **Interface Segregation:** mobile auth is a tiny token-provider port instead
  of leaking secure-storage details into payment and wallet code.
- **Dependency Inversion:** high-level client behavior depends on ports, not on
  concrete `fetch` or token-storage implementations.

## TDD Notes

- **Red:** mobile transport tests first referenced the missing fetch adapter and
  token-provider client configuration.
- **Green:** `FetchApiTransport`, `AccessTokenProvider`, and async token
  resolution made URL, response, network-error, and rotating-token tests pass.
- **Refactor:** readiness evidence, README benchmark, release runbook, and
  ubiquitous language now document the new mobile network boundary.

## Remaining Work

The port is ready for production integration, but the next slices should still
add OS keystore-backed token storage, token refresh behavior, retry/backoff,
offline queueing policy, and screen-level Redux async thunks.

# Phase 15 Mobile Token Session

This slice adds a mobile auth session boundary before wiring the app to native
secure storage:

- `TokenSession` models the mobile user ID, access token, refresh token, and
  access-token expiry;
- `SecureTokenVault` stores token sessions behind a tiny key-value secure-store
  port;
- `NativeSecureTokenStore` adapts a platform secure-store driver while enforcing
  hardened mobile storage options;
- corrupt stored sessions are cleared and surfaced as deterministic auth errors;
- `TokenSessionAccessTokenProvider` reuses cached access tokens before the
  refresh window;
- expiring or expired access tokens are refreshed through a `TokenRefreshPort`;
- refresh failures clear stored credentials so stale bearer tokens do not keep
  circulating through the API runtime.

The implementation keeps SOLID boundaries intact:

- **Single Responsibility:** the vault owns persistence, while the provider owns
  refresh timing.
- **Open/Closed:** Expo SecureStore, iOS Keychain, Android Keystore, or tests can
  implement the same storage port.
- **Liskov Substitution:** static, rotating, and session-backed token providers
  all satisfy the same `AccessTokenProvider` contract.
- **Interface Segregation:** refresh behavior is a focused function port, not a
  broad auth service dependency.
- **Dependency Inversion:** API clients depend on token-provider abstractions,
  not on native storage or refresh infrastructure.

## TDD Notes

- **Red:** token-session tests first referenced a missing secure vault and
  session-backed access-token provider.
- **Green:** vault persistence, native secure-store option forwarding,
  production storage readiness validation, corrupt-payload cleanup, cached-token
  reuse, refresh-window renewal, and refresh-failure cleanup made the tests pass.
- **Refactor:** readiness evidence, README benchmark, release runbook, and
  ubiquitous language now document the auth session boundary.

## Remaining Work

The native secure-store adapter port and readiness checks now exist. Real
production still needs the concrete iOS Keychain or Android Keystore package,
device QA, refresh calls wired to the API runtime, and screen-level Redux async
thunks driven from real mobile auth state.

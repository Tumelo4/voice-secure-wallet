# Phase 16 Mobile Redux API Flows

This slice connects the typed mobile API client to Redux-style request flows:

- `MobileApiState` tracks wallet-balance and payment-start request state;
- wallet and payment thunks dispatch requested, succeeded, and failed actions;
- API errors preserve status, code, and message for mobile screens;
- token-session errors map to unauthorized request state;
- trace IDs are captured when requests start;
- previous wallet data is preserved when a refresh request fails;
- the Redux store includes the `mobileApi` reducer;
- the readiness dashboard surfaces live wallet/payment request statuses.

The design keeps the mobile UI SOLID:

- **Single Responsibility:** components select state, thunks orchestrate calls,
  and the API client owns HTTP contract details.
- **Open/Closed:** future retry/backoff or offline flows can wrap the same
  action/reducer contract.
- **Liskov Substitution:** tests and production clients only need the
  `MobileApiClientPort` methods.
- **Interface Segregation:** wallet and payment flows depend on the smallest
  client surface they need.
- **Dependency Inversion:** Redux flows depend on the API client port, not a
  concrete fetch transport or token vault.

## TDD Notes

- **Red:** mobile API flow tests first referenced a missing reducer/thunk module.
- **Green:** the reducer, action union, and thunk-style async commands made
  wallet success, payment success, API failure, and auth failure branches pass.
- **Refactor:** the Redux store, dashboard status panel, readiness evidence,
  README benchmark, release runbook, and ubiquitous language now document the
  flow boundary.

## Remaining Work

Next slices should add real payment and wallet screens, user-triggered dispatch,
retry/backoff policy, native secure-storage wiring, and offline queueing UX.

# Phase 24 Mobile Screen Commands

This phase gives the React Native app screen-level command forms for the two
API flows already modeled: wallet balance reads and payment starts. It stays
dependency-free for CI by testing the form model directly, while the dashboard
now renders a command-form panel with mobile input controls and accessibility
labels.

## What Changed

- Added `mobileCommandForms.ts` for wallet-balance and payment-start form state.
- Added local validation for required wallet account IDs, required payment
  fields, positive payment amounts, and currency normalization.
- Added submit helpers that reuse the existing `loadWalletBalance` and
  `startPayment` Redux API thunks.
- Added `MobileCommandForms.tsx` to render React Native wallet/payment form
  controls in the readiness dashboard.
- Updated README, mobile README, release runbook, ubiquitous language, and
  readiness evidence.

## TDD Trail

- **Red:** command-form tests imported a missing `mobileCommandForms` module.
- **Green:** the form model, validation, typed command mapping, and submit
  helpers made wallet, payment, and invalid-form tests pass.
- **Refactor:** the React Native dashboard now shows screen command controls,
  and docs distinguish local form readiness from real production dependency
  injection.

## BDD/DDD Notes

- **BDD:** tests describe user behavior: wallet forms trim account IDs, payment
  forms build typed commands, and invalid forms fail locally before API calls.
- **DDD:** command forms belong to the mobile presentation boundary; they do not
  own payment, ledger, identity, fraud, or API runtime decisions.

## SOLID Notes

- **Single Responsibility:** form code owns input validation and command
  mapping; Redux thunks still own API request transitions.
- **Open/Closed:** future mobile screens can reuse the submit helpers without
  changing API clients or reducers.
- **Liskov Substitution:** tests and production screens use the same
  `MobileApiFlowDependencies` port.
- **Interface Segregation:** forms depend only on the wallet/payment API flow
  dependency interface, not on the whole store.
- **Dependency Inversion:** screen code depends on typed ports and thunks rather
  than concrete transport or token storage implementations.

## Still Not Production Complete

The forms are now present and locally validated, but production still needs the
real mobile API client dependencies injected into the screen callbacks, device
QA for keyboard/input behavior, optimistic UX polish, and end-to-end staging
checks against deployed ingress.

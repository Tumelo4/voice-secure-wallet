# VoiceSecure Mobile App

React Native TypeScript readiness dashboard for VoiceSecure Wallet.

## Problem Statement

The UI stack must match the product direction instead of living as a static web
page. A wallet team needs mobile-first screens, mobile state management, and a
styling system that can move toward production app flows.

## Impact

- Reviewers can inspect the same readiness evidence in a React Native codebase.
- Product UI work can continue with Redux state boundaries instead of static DOM
  rendering.
- Tailwind class names are available through NativeWind so mobile screens can
  stay fast to iterate without hand-rolled CSS files.

## Stack

- React Native through Expo and TypeScript.
- Tailwind CSS through NativeWind.
- Redux through Redux Toolkit and React Redux.

## Scope

This phase migrates the previous static readiness dashboard into `apps/mobile`.
It includes the app shell, Redux store, dashboard slice, TypeScript readiness
model, NativeWind/Tailwind configuration, and dependency-free Node tests for the
readiness state model used by the mobile UI.

It also includes a typed API client boundary for payment commands and wallet
balance reads. The client depends on an `ApiTransport` port, so React Native
`fetch`, deterministic tests, or future offline adapters can be swapped without
touching components. The current transport adapter wraps React Native `fetch`,
normalizes base URLs and paths, preserves response headers, maps network
failures into typed API errors, and resolves tokens through a provider port.
Token sessions are stored behind a vault port, so native secure storage can be
connected without changing API client or component code.
Redux API flows now bridge wallet-balance reads and payment-start commands into
request state that React Native screens can select and render.
Screen command forms now validate wallet-balance and payment-start input before
dispatching those Redux API flows.
Mobile resilience policy keeps retry/backoff and offline payment queue behavior
local and deterministic until durable Kafka/AWS-backed infrastructure is added.

## Benchmark

- The TypeScript state model declares React Native, NativeWind/Tailwind, and
  Redux Toolkit as the UI stack.
- Summary cards expose mobile accessibility labels.
- Dashboard sections preserve summary, phases, risks, and evidence order.
- Tailwind class tokens cover the mobile screen, cards, active phase, and metric
  grid.
- API client tests prove payment POST headers/body, wallet GET mapping,
  runtime error handling, and Redux-friendly async request transitions.
- Command form tests prove wallet account trimming, typed payment command
  construction, user-triggered Redux dispatch, and local validation blockers
  before API calls.
- Fetch transport tests prove URL joining, request forwarding, response mapping,
  deterministic network failures, and fresh token-provider reads per request.
- Token session tests prove secure vault save/load/clear, corrupt payload
  cleanup, cached access-token reuse, refresh-window renewal, and refresh-failure
  credential cleanup.
- Redux API flow tests prove wallet success, payment success, API failure,
  auth-session failure, trace preservation, and previous-data preservation.
- Resilience policy tests prove retry backoff/capping, non-retryable auth and
  validation failures, idempotent offline payment enqueue, local queue-depth
  limits, ordered drain, and retryable blocker preservation.

## How To Use It

Install dependencies from this folder, then start the Expo app:

```sh
npm install
npm run start
```

Run the dependency-free state model tests:

```sh
npm test
```

The test command uses Node 24 native TypeScript execution for the small
dependency-free state model checks.

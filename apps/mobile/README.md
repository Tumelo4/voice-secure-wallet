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
touching components.

## Benchmark

- The TypeScript state model declares React Native, NativeWind/Tailwind, and
  Redux Toolkit as the UI stack.
- Summary cards expose mobile accessibility labels.
- Dashboard sections preserve summary, phases, risks, and evidence order.
- Tailwind class tokens cover the mobile screen, cards, active phase, and metric
  grid.
- API client tests prove payment POST headers/body, wallet GET mapping,
  runtime error handling, and Redux-friendly async request transitions.

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

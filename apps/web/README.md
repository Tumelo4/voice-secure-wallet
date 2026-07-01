# VoiceSecure Web Dashboard

Static readiness dashboard for the VoiceSecure Wallet build.

## Problem Statement

The PDF plan has many phase gates, services, tests, and launch blockers.
Without a clear UI, the project can look complete just because code merged,
even when production evidence is still missing.

## Impact

- Reviewers can see which service slices are ready and which production gates
  remain.
- The team has one place to explain TDD, BDD, DDD, CI, and launch evidence.
- The dashboard gives non-technical stakeholders a safer view than raw commit
  history.

## Scope

This is a dependency-free static UI. It renders local readiness data from
`src/dashboard.mjs` and is intended to evolve into a real authenticated admin
surface later.

## Benchmark

- Dashboard model should summarize all current validation evidence.
- Phase timeline should preserve the PDF phase order.
- Risk panel should highlight durable infrastructure adapters, network server
  integration, Terraform, Pact, and launch evidence as remaining blockers.
- Summary cards should render accessible labels.

## How To Use It

Open `apps/web/index.html` in a browser or serve the repository with any static
file server.

Run the UI tests:

```sh
node --test apps/web/test/dashboard.test.mjs
```

No package install is required.

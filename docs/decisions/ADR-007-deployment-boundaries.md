# ADR-007: Deployment boundaries before first production release

Status: Accepted

## Context

The reactor contains modules named as services, but a directory name is not a
deployment boundary. Splitting every module into a separate runtime before a
proven deployment would multiply authentication, telemetry, release and on-call
surface without demonstrated scaling or ownership pressure.

## Decision

The Java modules remain one modular-monolith deployment behind
`api-adapter-service`. `ledger`, `payment`, and `identity` remain strong internal
modules because they own materially different money, orchestration and
authentication policies, but they are not independently deployed today.

`support`, `recovery`, `ops`, and `launch` are explicitly not separate
deployables: support/recovery are internal modules; ops/launch are build-time
policy validators. Notification, wallet, beneficiary, fraud and compliance also
remain internal modules until a named team, independent scaling requirement and
release cadence are evidenced in a replacement ADR.

The Python voice runtime remains separate because it has a different language,
model/runtime resource profile and biometric isolation boundary.

## Consequences

- One Java runtime owns ingress, authentication, tracing and operational policy.
- Package/Maven boundaries remain future extraction seams.
- A new Java deployment requires an ADR naming ownership, scaling, data, SLO,
  security and release responsibilities before code creates another runtime.

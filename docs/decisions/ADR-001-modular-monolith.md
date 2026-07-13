# ADR-001: Java modular monolith

Status: Accepted

## Context

The Java reactor contains many modules named `*-service`, but only the API adapter owns a runtime entry point. Separate deployment, configuration, health, data ownership, SLO and runbook evidence is absent for the other Java modules.

## Decision

Deploy the Java code as one modular application. Preserve business boundaries through Maven/package dependencies and architecture tests. A module may become an independently deployed service only after it owns all operational responsibilities required by the remediation plan. The Python biometric runtime remains a candidate independent deployment behind a port.

## Consequences

- In-process calls replace fictional network boundaries.
- Transactions can span application coordination and an outbox without distributed orchestration overhead.
- Module ownership and dependency direction remain mandatory.
- Existing `-service` directory names are legacy locations until a controlled move avoids breaking migrations and evidence.


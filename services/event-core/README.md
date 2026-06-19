# event-core

Shared event envelope and outbox relay utilities for VoiceSecure Wallet.

## Problem Statement

When every service invents its own event shape, consumers break, audit trails
fracture, and delivery semantics become hard to reason about. The platform needs
one common contract for event publication and relay behavior.

## Impact

- Engineers can add new workflows without inventing a new event format each
  time.
- Consumers receive predictable, auditable messages across the system.
- The business gets a cleaner integration backbone and fewer cross-service
  defects.

## Scope

This package defines the common shape for domain events across payment, ledger,
fraud, identity, voice, and compliance flows.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

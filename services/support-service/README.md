# support-service

Support operations core for VoiceSecure Wallet.

## Problem Statement

When customers contact support, the team needs to search transactions, freeze
accounts, and handle disputes without guessing at the underlying state. If those
actions are detached from the ledger, support becomes slow and risky.

## Impact

- Users get faster resolution and clearer explanations for account issues.
- Support agents can act with confidence because every action stays audit-backed.
- The business reduces handling time, escalations, and the chance of unsafe
  manual fixes.

## Scope

This service keeps a read-only ledger replica for transaction search, tracks
account freeze and dispute cases, and links repair escalations to the ledger
repair flow.

## Current Guarantees

- Search is driven from a projected ledger replica.
- Account freezes and disputes are audit-backed.
- Repair requests remain linked to the signed ledger repair path.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

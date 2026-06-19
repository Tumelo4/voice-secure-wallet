# compliance-service

Java 17 compliance core for VoiceSecure Wallet.

## Problem Statement

Regulated financial systems need deterministic screening for PEP, sanctions,
and AML requirements. If compliance is inconsistent, the platform risks
regulatory breach, failed banking relationships, and weak audit evidence.

## Impact

- Users avoid surprise holds because compliance checks are predictable and
  traceable.
- Compliance and audit teams get a durable decision trail.
- The business stays aligned with regulatory expectations and protects its
  ability to operate at scale.

## Scope

This service screens identities and transactions against PEP, sanctions, and AML
rules and keeps its own audit trail.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```

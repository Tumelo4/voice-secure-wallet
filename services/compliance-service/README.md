# compliance-service

Java 17 compliance core for VoiceSecure Wallet.

This service screens identities and transactions against PEP, sanctions, and AML
rules and keeps its own audit trail.

## Benchmark

- PEP and sanctions matches should resolve in constant time for the in-memory
  watchlists.
- AML threshold checks should append exactly one audit entry per screening.
- Screening latency should stay under 5 ms locally for deterministic tests.

## How To Use It

Seed the in-memory repository with watchlist data, then screen a profile:

```java
ComplianceService compliance = new ComplianceService(new InMemoryComplianceRepository());
compliance.addPep("9001015009087", "pep");
ComplianceScreeningResult result = compliance.screen(profile);
```

Use `auditTrail()` to inspect the screening evidence produced by each decision.

## Local Test Command

```powershell
powershell -ExecutionPolicy Bypass -File ..\..\scripts\test-services.ps1
```


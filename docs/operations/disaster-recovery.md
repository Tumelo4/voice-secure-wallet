# Disaster recovery

Restore order is secrets/identity trust, PostgreSQL, application, outbox relay, broker consumers, voice and reporting. Validate migration level, journal control totals, outbox backlog and projections before reopening writes. Recovery drills must record RPO/RTO, evidence, discrepancies and actions. A document without a successful drill is not operational validation.

Phase 3 sign-off must be recorded in a non-example manifest and pass
`python3 scripts/validate-phase3-evidence.py <manifest>`. The validator requires
measured RTO/RPO, 48 hours of SLO history, live ingress controls, and named
independent PCI and biometric assessors; policy documents cannot satisfy it.

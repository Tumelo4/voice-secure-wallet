# Runbooks

Alerts must identify owner, severity, customer impact and trace reference. For stuck payments: stop unsafe retries, inspect immutable history, query provider, enter reconciliation and use dual-control repair only when evidence agrees. For database/broker outages: preserve intake idempotency, degrade to pending, restore service, replay outbox and reconcile. For voice outage: enforce value/risk policy and fallback MFA. Never mark an unknown provider result failed without reconciliation.


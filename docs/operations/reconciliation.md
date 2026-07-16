# Reconciliation operations

Ingest signed provider settlement files into an immutable staging area. Match provider reference, payment, currency and amount against ledger batches. Report missing, duplicate and mismatched records separately. Operators cannot edit ledger entries; approved corrections create linked compensating journals. Daily control totals and unresolved ageing are retained as evidence.

The payment production runtime runs a single-owner recovery scan using a
PostgreSQL advisory lock. Each observed stuck saga, state, selected recovery
action, trace identifier, age, and timestamp is appended to
`payment_recovery_audit`. Multiple application instances may schedule the job,
but only the lock holder performs a scan window.

# ADR-005: Transactional outbox

Status: Accepted

State mutations and their outgoing event records must commit in the same database transaction. A relay publishes outbox records at least once, while consumers deduplicate by event identifier. Broker acknowledgement alone never substitutes for the database transaction. Relay recovery, poison messages and backlog alerts are required before production operation.


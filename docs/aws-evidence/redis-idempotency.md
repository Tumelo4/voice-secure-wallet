# Redis idempotency evidence

Status: live test not yet run.

Capture a TLS connection, a redacted idempotency key, the first accepted payment request, the duplicate rejection, and relevant cache metrics. Record the TTL and resource deletion time without committing secrets or customer data.

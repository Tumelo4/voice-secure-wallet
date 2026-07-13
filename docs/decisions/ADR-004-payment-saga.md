# ADR-004: Durable payment saga

Status: Accepted

Payment state changes are domain-controlled and append-only. A server-generated saga identifier is internal; the public API returns a payment identifier/reference. Every transition carries a version for optimistic concurrency. Terminal completion is monotonic. Unknown provider outcomes enter reconciliation rather than failure, and compensation is recorded as an attempt that may itself require manual review.


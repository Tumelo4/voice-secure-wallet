# ADR-002: Money representation

Status: Accepted

## Decision

Authoritative API amounts are decimal strings paired with validated ISO 4217 currency codes. Java parses them into `BigDecimal` and `Currency` through the `Money` domain type, validates positivity and currency scale, and converts explicitly to integral minor units for existing ledger storage. TypeScript payment models keep amounts as strings and never use floating point for validation or submission.

Rounding is forbidden at payment boundaries. Values with excess scale are rejected; callers must present an exact amount appropriate to the currency.


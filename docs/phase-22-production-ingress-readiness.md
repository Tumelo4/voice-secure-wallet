# Phase 22 Production Ingress Readiness

This phase hardens the API edge before live cloud work. It still does not
provision certificates, DNS, load balancers, or AWS resources; it makes the
production ingress policy executable so unsafe ingress configurations are
blocked locally and in CI.

## What Changed

- Added `ProductionIngressPlan` to describe edge deployment evidence.
- Added `ProductionIngressPolicy` with TLS 1.3, 256 KB request body, and
  health-only public path defaults.
- Added `ProductionIngressValidator` and `ProductionIngressValidationReport`.
- Added `TlsVersion` for explicit TLS version comparison.
- Added service-level tests for valid ingress, transport security gaps, and
  runtime control gaps.
- Updated README, API README, release runbook, ubiquitous language, and mobile
  readiness evidence.

## TDD Trail

- **Red:** production ingress tests referenced missing ingress plan, policy,
  validator, report, and TLS version types.
- **Green:** the minimal value objects and validator made the transport and
  runtime-control tests pass.
- **Refactor:** readiness evidence and documentation now describe production
  ingress as a preflight gate rather than a live infrastructure deployment.

## BDD/DDD Notes

- **BDD:** test names describe business outcomes: transport security gaps and
  runtime control gaps block production ingress.
- **DDD:** `api-adapter-service` owns ingress readiness policy because it owns
  the request boundary, but it does not own cloud provisioning, DNS, or
  certificate issuance.

## SOLID Notes

- **Single Responsibility:** `ProductionIngressValidator` only validates edge
  readiness evidence.
- **Open/Closed:** API routing and runtime classes were not changed to add a
  production preflight.
- **Liskov Substitution:** stricter future ingress policies can change TLS,
  body-size, or public-path defaults without changing callers.
- **Interface Segregation:** runtime ports remain separate from ingress
  readiness models.
- **Dependency Inversion:** ingress readiness depends on policy/value objects,
  not on AWS ALB, CloudFront, Kubernetes, or certificate-vendor APIs.

## Still Not Production Complete

Real production readiness still requires live certificate provisioning, mTLS
trust-store setup, DNS, load balancer deployment, JWKS discovery against the
external identity provider, distributed rate-limit backing storage, WAF/HSTS
edge configuration, and staging smoke tests against the deployed ingress.

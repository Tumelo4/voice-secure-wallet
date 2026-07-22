# Repository ownership

`CODEOWNERS` identifies the current accountable maintainer, but it does not yet
provide independent review. Before branch protection is made mandatory, assign
and add a second GitHub maintainer for every high-risk area below.

| Area | Accountable owner | Required secondary | Operational responsibility |
|---|---|---|---|
| Payment, ledger and reconciliation | `@Tumelo4` | Unassigned | Money invariants, recovery, settlement and repair controls |
| API and identity boundary | `@Tumelo4` | Unassigned | Authentication, authorization, request contracts and ingress |
| Mobile and voice | `@Tumelo4` | Unassigned | Device security, biometric boundary and customer journeys |
| Infrastructure and delivery | `@Tumelo4` | Unassigned | Terraform, CI, artifacts, deployment and rollback |

Changes to money movement, authentication, migrations, CI permissions or
infrastructure require a domain-owner review plus the secondary reviewer once
assigned. Production incidents use the service runbooks and must name an
incident commander independently of the change author.

## Enforcement status

- [The merge policy](engineering/merge-policy.md) defines high-risk path
  evidence and the exact hosted ruleset that must enforce independent review.
- Pull-request CI classifies high-risk changes and runs their repository-side
  evidence gates. Approval counts and author independence remain GitHub
  ruleset responsibilities.
- Maven now rejects dependency convergence conflicts across the complete Java
  reactor; direct constraints align SLF4J, Apache HttpCore, Commons Codec and
  Jakarta Annotations across the AWS, Redis and HTTP runtime graphs.
- Payment recovery has native JUnit coverage and PostgreSQL fault tests for
  multi-instance creation, reservation/commit crash windows, compensation
  restart and concurrent expired-reservation consumption.
- `@Tumelo4` remains the only configured owner. Independent approval cannot be
  claimed until a real second maintainer is selected and added to CODEOWNERS.

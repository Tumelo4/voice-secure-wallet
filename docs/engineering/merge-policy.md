# Merge and independent-review policy

All changes reach `main` through a pull request. The author cannot provide the
independent approval. Conversations must be resolved and approvals are stale
after the reviewed commit changes. Force pushes and branch deletion are not
permitted on `main`.

The machine-readable policy is [`.github/merge-policy.yml`](../../.github/merge-policy.yml).
It classifies high-risk paths and the evidence required for them. The required
PR workflow executes the corresponding validation when those paths change.

| Change | Minimum independent approvals | Additional evidence |
|---|---:|---|
| Normal repository change | 1 | Required PR checks |
| Payment, ledger or integration harness | 2 | PostgreSQL integration profile |
| Identity, voice or public API boundary | 2 | Security-boundary tests and static analysis |
| Database migration | 2 | Migration validation |
| Terraform | 2 | Format and validation |
| Workflow or ownership policy | 2 | Delivery-governance owner review |
| API/event contract | 2 | Contract compatibility tests |

## GitHub ruleset required

Repository administrators must mirror this policy in a ruleset targeting
`main`: require pull requests, the `Required pull request checks` workflow,
CODEOWNER review, conversation resolution, dismissal of stale approvals, and
linear or squash history; block force pushes, deletion and direct pushes; and
apply the rules to administrators. GitHub configuration is the enforcement
boundary for approval counts and author independence.

The repository currently has one named owner. Two-approval rules must not be
described as operational until a real secondary maintainer is granted access
and the hosted ruleset is enabled. Do not add a placeholder identity merely to
make CODEOWNERS appear complete.

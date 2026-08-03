# AWS staging deployment

The staging plan can run through GitHub Actions OIDC with short-lived AWS
credentials. Applying remains a deliberate operator action protected by the
account and confirmation guards below.

## Preconditions

- Use a dedicated staging AWS account.
- Apply `infra/aws/bootstrap` first to create the remote-state S3 bucket and
  DynamoDB lock table. The bootstrap also creates the GitHub OIDC provider and
  the repository-scoped `voice-secure-wallet` role. These resources cannot be created by the same operation
  that consumes the remote backend.
- Supply the Redis token through an environment variable or approved secret
  broker. Never store the real value in a tfvars file.
- Record the change ticket and approved operator before apply.

## Plan

### GitHub Actions OIDC

Apply the bootstrap once using an approved administrative identity:

```bash
terraform -chdir=infra/aws/bootstrap init
terraform -chdir=infra/aws/bootstrap apply
```

Configure the repository secret `TF_VAR_REDIS_AUTH_TOKEN`. Both AWS workflows
assume the existing
`arn:aws:iam::296032707614:role/voice-secure-wallet` role.

Run the `AWS staging plan` workflow manually from `main`. Its trust policy
accepts only the
`repo:Tumelo4/voice-secure-wallet:ref:refs/heads/main` subject and the
`sts.amazonaws.com` audience in AWS account `296032707614`. The workflow
receives temporary credentials and cannot apply infrastructure.

The `AWS staging apply` workflow runs only from `main`, assumes the existing
branch-scoped deployment role, verifies account `296032707614`, generates a
fresh saved plan from `infra/aws/environments/staging`, and applies that exact
plan. Staging uses the `environments/staging.tfstate` state key and the
`voicesecure-staging` resource prefix; it never targets the production-reference
state or resource names.

The shared role has `ReadOnlyAccess`, encrypted Terraform-state access, ECR
push permissions for `voice-secure-wallet-*`, ECS deployment permissions for
project services, and `iam:PassRole` restricted to project ECS task roles. Its
`staging-foundation-apply-access` inline policy permits writes only for the
cost-controlled staging foundation: tagged VPC networking, the
`alias/voicesecure-staging-platform` KMS key, the two
`voicesecure-staging-*` audit buckets, `/voicesecure-staging/*` log groups, and
the `voicesecure-staging-vpc-flow` service role. APIs that cannot be fully
resource-scoped require both `Environment=staging` and `ManagedBy=terraform`
request or resource tags. The role has no RDS, ElastiCache, or MSK write
actions and does not have `PowerUserAccess` or `AdministratorAccess`.

### Local AWS SSO/profile

For an operator plan, authenticate a named AWS CLI profile through IAM Identity
Center or another approved short-lived credential source:

```bash
export AWS_PROFILE=voicesecure-staging
export EXPECTED_AWS_ACCOUNT_ID=123456789012
export TF_VAR_redis_auth_token='value-from-approved-secret-broker'
scripts/aws-staging-deployment.sh plan
```

The script also accepts ambient OIDC/web-identity credentials when
`AWS_PROFILE` is unset.

Review `terraform show infra/aws/environments/staging/staging.tfplan`
and obtain approval. To apply,
set `CONFIRM_STAGING_APPLY=staging:$EXPECTED_AWS_ACCOUNT_ID` and rerun the script
with `apply`. The account comparison prevents an authenticated production or
personal account from being targeted accidentally.

After apply, capture the Terraform outputs, CloudTrail change events, resource
health, encryption settings, MSK IAM endpoint, RDS connectivity evidence, and
rollback decision in the approved evidence store.

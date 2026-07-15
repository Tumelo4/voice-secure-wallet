# AWS staging deployment

Automatic GitHub workflows are temporarily manual-only while Phase 2 is being
completed. This does not weaken the staging apply guard below.

## Preconditions

- Use a dedicated staging AWS account and a named AWS CLI profile.
- Bootstrap the remote-state S3 bucket and DynamoDB lock table before running
  this root module; they cannot be created by the same operation that consumes
  them as a Terraform backend.
- Supply rotation Lambda ARNs and the Redis token through environment variables
  or an approved secret broker. Never store those values in a tfvars file.
- Record the change ticket and approved operator before apply.

## Plan

```bash
export AWS_PROFILE=voicesecure-staging
export EXPECTED_AWS_ACCOUNT_ID=123456789012
export TF_VAR_database_secret_rotation_lambda_arn=arn:aws:lambda:af-south-1:123456789012:function:rotate-rds
export TF_VAR_redis_secret_rotation_lambda_arn=arn:aws:lambda:af-south-1:123456789012:function:rotate-redis
export TF_VAR_redis_auth_token='value-from-approved-secret-broker'
scripts/aws-staging-deployment.sh plan
```

Review `terraform show infra/aws/staging.tfplan` and obtain approval. To apply,
set `CONFIRM_STAGING_APPLY=staging:$EXPECTED_AWS_ACCOUNT_ID` and rerun the script
with `apply`. The account comparison prevents an authenticated production or
personal account from being targeted accidentally.

After apply, capture the Terraform outputs, CloudTrail change events, resource
health, encryption settings, MSK IAM endpoint, RDS connectivity evidence, and
rollback decision in the approved evidence store.

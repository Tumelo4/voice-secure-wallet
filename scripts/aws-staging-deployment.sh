#!/usr/bin/env bash
set -euo pipefail

action="${1:-plan}"
if [[ "$action" != "plan" && "$action" != "apply" ]]; then
  echo "usage: $0 [plan|apply]" >&2
  exit 64
fi

required=(AWS_PROFILE EXPECTED_AWS_ACCOUNT_ID TF_VAR_database_secret_rotation_lambda_arn TF_VAR_redis_secret_rotation_lambda_arn TF_VAR_redis_auth_token)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "$name must be set" >&2
    exit 65
  fi
done

command -v aws >/dev/null || { echo "aws CLI is required" >&2; exit 69; }
command -v terraform >/dev/null || { echo "terraform is required" >&2; exit 69; }

export AWS_REGION="${AWS_REGION:-af-south-1}"
export AWS_DEFAULT_REGION="$AWS_REGION"
actual_account="$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query Account --output text)"
if [[ "$actual_account" != "$EXPECTED_AWS_ACCOUNT_ID" ]]; then
  echo "refusing deployment: authenticated account $actual_account does not match EXPECTED_AWS_ACCOUNT_ID" >&2
  exit 77
fi

terraform -chdir=infra/aws init -input=false
terraform -chdir=infra/aws validate
terraform -chdir=infra/aws plan -input=false -out=staging.tfplan -var='environment=staging'

if [[ "$action" == "apply" ]]; then
  if [[ "${CONFIRM_STAGING_APPLY:-}" != "staging:${EXPECTED_AWS_ACCOUNT_ID}" ]]; then
    echo "set CONFIRM_STAGING_APPLY=staging:${EXPECTED_AWS_ACCOUNT_ID} to authorize apply" >&2
    exit 77
  fi
  terraform -chdir=infra/aws apply -input=false staging.tfplan
fi

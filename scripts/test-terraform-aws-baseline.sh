#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA_DIR="$ROOT_DIR/infra/aws"

if ! command -v terraform >/dev/null 2>&1; then
  echo "terraform is required to validate the AWS baseline" >&2
  exit 1
fi

terraform -chdir="$INFRA_DIR" fmt -check -recursive
for root in bootstrap environments/demo environments/production-reference; do
  TF_DATA_DIR="$(mktemp -d /private/tmp/voice-secure-terraform-XXXXXX)"
  TF_DATA_DIR="$TF_DATA_DIR" terraform -chdir="$INFRA_DIR/$root" init -backend=false -input=false -no-color
  TF_DATA_DIR="$TF_DATA_DIR" terraform -chdir="$INFRA_DIR/$root" validate -no-color
  rm -rf "$TF_DATA_DIR"
done

echo "Terraform AWS modules, bootstrap, demo and production-reference validation passed"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA_DIR="$ROOT_DIR/infra/aws"
TF_DATA_DIR="$(mktemp -d /private/tmp/voice-secure-terraform-XXXXXX)"
trap 'rm -rf "$TF_DATA_DIR"' EXIT

if ! command -v terraform >/dev/null 2>&1; then
  echo "terraform is required to validate the AWS baseline" >&2
  exit 1
fi

TF_DATA_DIR="$TF_DATA_DIR" terraform -chdir="$INFRA_DIR" fmt -check -recursive
TF_DATA_DIR="$TF_DATA_DIR" terraform -chdir="$INFRA_DIR" init -backend=false -input=false -no-color
TF_DATA_DIR="$TF_DATA_DIR" terraform -chdir="$INFRA_DIR" validate -no-color

echo "Terraform AWS baseline validation passed"

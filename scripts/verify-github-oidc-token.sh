#!/usr/bin/env bash
set -euo pipefail

: "${ACTIONS_ID_TOKEN_REQUEST_URL:?GitHub OIDC request URL is unavailable}"
: "${ACTIONS_ID_TOKEN_REQUEST_TOKEN:?GitHub OIDC request token is unavailable}"

response="$(curl --fail --silent --show-error \
  --header "Authorization: bearer ${ACTIONS_ID_TOKEN_REQUEST_TOKEN}" \
  "${ACTIONS_ID_TOKEN_REQUEST_URL}&audience=sts.amazonaws.com")"
id_token="$(jq --exit-status --raw-output '.value' <<<"${response}")"

OIDC_ID_TOKEN="${id_token}" python3 - <<'PY'
import base64
import json
import os
import sys


def decode_part(value: str) -> dict:
    value += "=" * (-len(value) % 4)
    return json.loads(base64.urlsafe_b64decode(value))


token = os.environ["OIDC_ID_TOKEN"]
parts = token.split(".")
if len(parts) != 3:
    raise SystemExit("GitHub returned a malformed OIDC token")

header = decode_part(parts[0])
claims = decode_part(parts[1])
safe_claims = {
    "iss": claims.get("iss"),
    "aud": claims.get("aud"),
    "sub": claims.get("sub"),
    "repository": claims.get("repository"),
    "ref": claims.get("ref"),
    "environment": claims.get("environment"),
    "kid": header.get("kid"),
}
print("GitHub OIDC claims (token omitted):")
print(json.dumps(safe_claims, indent=2, sort_keys=True))

expected = {
    "iss": "https://token.actions.githubusercontent.com",
    "aud": "sts.amazonaws.com",
    "repository": "Tumelo4/voice-secure-wallet",
}
invalid = [key for key, value in expected.items() if claims.get(key) != value]
if invalid:
    print(f"Unexpected OIDC claim(s): {', '.join(invalid)}", file=sys.stderr)
    raise SystemExit(1)

allowed_subjects = {
    "repo:Tumelo4/voice-secure-wallet:ref:refs/heads/main",
    "repo:Tumelo4/voice-secure-wallet:environment:staging",
}
if claims.get("sub") not in allowed_subjects:
    print("Unexpected OIDC subject", file=sys.stderr)
    raise SystemExit(1)
PY

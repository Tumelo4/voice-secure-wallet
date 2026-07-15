#!/usr/bin/env python3
"""Fail-closed validator for operational and independent Phase 3 evidence."""

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

REQUIRED = {"telemetry_slo", "disaster_recovery", "production_ingress", "pci_dss", "biometric_evaluation"}
PLACEHOLDERS = {"", "pending", "todo", "tbd", "example", "not-assessed", "not-run"}


def present(value):
    return isinstance(value, str) and value.strip().lower() not in PLACEHOLDERS


def validate(document):
    errors = []
    if document.get("environment") not in {"staging", "production"}:
        errors.append("environment must be staging or production")
    controls = document.get("controls")
    if not isinstance(controls, list):
        return errors + ["controls must be a list"]
    by_type = {item.get("type"): item for item in controls if isinstance(item, dict)}
    for control_type in sorted(REQUIRED - by_type.keys()):
        errors.append(f"missing control: {control_type}")
    for control_type in sorted(REQUIRED & by_type.keys()):
        item = by_type[control_type]
        for field in ("run_id", "completed_at", "evidence_uri", "approver"):
            if not present(item.get(field)):
                errors.append(f"{control_type}.{field} must contain real evidence")
        if item.get("passed") is not True:
            errors.append(f"{control_type}.passed must be true")
        try:
            completed = datetime.fromisoformat(str(item.get("completed_at", "")).replace("Z", "+00:00"))
            if completed.tzinfo is None or completed > datetime.now(timezone.utc):
                errors.append(f"{control_type}.completed_at must be a past timezone-aware timestamp")
        except ValueError:
            errors.append(f"{control_type}.completed_at must be ISO-8601")
    telemetry = by_type.get("telemetry_slo", {})
    if telemetry.get("observation_hours", 0) < 48:
        errors.append("telemetry_slo.observation_hours must be at least 48")
    dr = by_type.get("disaster_recovery", {})
    if not 0 < dr.get("measured_rto_minutes", 0) <= dr.get("target_rto_minutes", 0):
        errors.append("disaster_recovery measured RTO must meet its positive target")
    if not 0 <= dr.get("measured_rpo_minutes", -1) <= dr.get("target_rpo_minutes", -1):
        errors.append("disaster_recovery measured RPO must meet its non-negative target")
    ingress = by_type.get("production_ingress", {})
    for control in ("tls13", "mtls", "oidc_jwks", "waf"):
        if ingress.get(control) is not True:
            errors.append(f"production_ingress.{control} must be true")
    for independent in ("pci_dss", "biometric_evaluation"):
        if by_type.get(independent, {}).get("independent_assessor") is not True:
            errors.append(f"{independent}.independent_assessor must be true")
    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    errors = validate(json.loads(args.manifest.read_text()))
    if errors:
        for error in errors:
            print(f"BLOCKED: {error}")
        raise SystemExit(1)
    print("Phase 3 operational and security evidence is complete")


if __name__ == "__main__":
    main()

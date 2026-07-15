#!/usr/bin/env python3
"""Validate measured full-system launch evidence without accepting assertions."""

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

REQUIRED = {"chaos", "security_scan", "penetration_test", "voice_shadow_mode",
            "load_test", "staging_stint", "cutover_drill"}
PLACEHOLDERS = {"", "tbd", "todo", "pending", "example", "not-run"}


def real(value):
    return isinstance(value, str) and value.strip().lower() not in PLACEHOLDERS


def validate(document):
    errors = []
    runs = document.get("runs")
    if not isinstance(runs, list):
        return ["runs must be a list"]
    by_type = {run.get("type"): run for run in runs if isinstance(run, dict)}
    for run_type in sorted(REQUIRED - by_type.keys()):
        errors.append(f"missing run: {run_type}")
    for run_type in sorted(REQUIRED & by_type.keys()):
        run = by_type[run_type]
        for field in ("run_id", "completed_at", "evidence_uri", "approver"):
            if not real(run.get(field)):
                errors.append(f"{run_type}.{field} must contain real evidence")
        if run.get("passed") is not True:
            errors.append(f"{run_type}.passed must be true")
        try:
            completed = datetime.fromisoformat(str(run.get("completed_at", "")).replace("Z", "+00:00"))
            if completed.tzinfo is None or completed > datetime.now(timezone.utc):
                errors.append(f"{run_type}.completed_at must be a past timezone-aware timestamp")
        except ValueError:
            errors.append(f"{run_type}.completed_at must be ISO-8601")
    load = by_type.get("load_test", {})
    if load.get("load_multiplier", 0) < 10:
        errors.append("load_test.load_multiplier must be at least 10")
    if load.get("p99_within_slo") is not True:
        errors.append("load_test.p99_within_slo must be true")
    stint = by_type.get("staging_stint", {})
    if stint.get("continuous_hours", 0) < 48:
        errors.append("staging_stint.continuous_hours must be at least 48")
    if stint.get("slo_breaches", 1) != 0:
        errors.append("staging_stint.slo_breaches must be zero")
    cutover = by_type.get("cutover_drill", {})
    if cutover.get("rollback_executed") is not True or cutover.get("rollback_minutes", 0) <= 0:
        errors.append("cutover_drill must include a measured rollback")
    pen = by_type.get("penetration_test", {})
    if pen.get("independent_tester") is not True or pen.get("open_critical_findings", 1) != 0:
        errors.append("penetration_test must be independent with zero open critical findings")
    shadow = by_type.get("voice_shadow_mode", {})
    if shadow.get("authorised_transactions", 1) != 0:
        errors.append("voice_shadow_mode must not authorise transactions")
    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    errors = validate(json.loads(args.manifest.read_text()))
    if errors:
        print("\n".join(f"BLOCKED: {error}" for error in errors))
        raise SystemExit(1)
    print("Phase 4 full-system evidence is complete")


if __name__ == "__main__":
    main()

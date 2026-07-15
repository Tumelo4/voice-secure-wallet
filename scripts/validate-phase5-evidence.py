#!/usr/bin/env python3
"""Fail closed on incomplete cutover and post-launch operating evidence."""

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

REQUIRED_SIGNOFFS = {"release_owner", "platform_owner", "payments_owner", "security_owner", "operations_owner"}
PLACEHOLDERS = {"", "tbd", "todo", "pending", "example", "not-run"}


def real(value):
    return isinstance(value, str) and value.strip().lower() not in PLACEHOLDERS


def past_timestamp(value, field, errors):
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
        if parsed.tzinfo is None or parsed > datetime.now(timezone.utc):
            errors.append(f"{field} must be a past timezone-aware timestamp")
    except ValueError:
        errors.append(f"{field} must be ISO-8601")


def validate(document):
    errors = []
    cutover = document.get("cutover", {})
    for field in ("release_digest", "change_ticket", "evidence_uri", "completed_at"):
        if not real(cutover.get(field)):
            errors.append(f"cutover.{field} must contain real evidence")
    past_timestamp(cutover.get("completed_at", ""), "cutover.completed_at", errors)
    if cutover.get("immutable_digest_promoted") is not True:
        errors.append("cutover.immutable_digest_promoted must be true")
    if cutover.get("rollback_armed") is not True:
        errors.append("cutover.rollback_armed must be true")
    signoffs = cutover.get("signoffs", {})
    for owner in sorted(REQUIRED_SIGNOFFS):
        value = signoffs.get(owner, {})
        if not real(value.get("name")) or not real(value.get("approved_at")):
            errors.append(f"cutover.signoffs.{owner} requires a named, timestamped approval")

    monitoring = document.get("post_launch_monitoring", {})
    if monitoring.get("continuous_hours", 0) < 168:
        errors.append("post_launch_monitoring.continuous_hours must cover the first week (168 hours)")
    if monitoring.get("unresolved_critical_incidents", 1) != 0:
        errors.append("post_launch_monitoring.unresolved_critical_incidents must be zero")
    for field in ("slo_dashboard_uri", "incident_log_uri", "owner"):
        if not real(monitoring.get(field)):
            errors.append(f"post_launch_monitoring.{field} must contain real evidence")

    review = document.get("first_week_review", {})
    for field in ("completed_at", "report_uri", "owner"):
        if not real(review.get(field)):
            errors.append(f"first_week_review.{field} must contain real evidence")
    past_timestamp(review.get("completed_at", ""), "first_week_review.completed_at", errors)
    if review.get("actions_tracked") is not True:
        errors.append("first_week_review.actions_tracked must be true")

    improvement = document.get("continuous_improvement", {})
    if improvement.get("cadence_days", 0) <= 0 or improvement.get("cadence_days", 999) > 90:
        errors.append("continuous_improvement.cadence_days must be between 1 and 90")
    for field in ("backlog_uri", "next_review_at", "owner"):
        if not real(improvement.get(field)):
            errors.append(f"continuous_improvement.{field} must contain real evidence")
    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    errors = validate(json.loads(args.manifest.read_text()))
    if errors:
        print("\n".join(f"BLOCKED: {error}" for error in errors))
        raise SystemExit(1)
    print("Phase 5 launch and monitoring evidence is complete")


if __name__ == "__main__":
    main()

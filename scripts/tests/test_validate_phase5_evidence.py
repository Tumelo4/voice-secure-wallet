import importlib.util
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

PATH = Path(__file__).parents[1] / "validate-phase5-evidence.py"
SPEC = importlib.util.spec_from_file_location("phase5", PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class Phase5EvidenceTests(unittest.TestCase):
    def valid(self):
        completed = (datetime.now(timezone.utc) - timedelta(days=8)).isoformat()
        approval = {"name": "named-owner", "approved_at": completed}
        return {
            "cutover": {"release_digest": "sha256:" + "a" * 64, "change_ticket": "CHG-123",
                        "evidence_uri": "s3://evidence/cutover.json", "completed_at": completed,
                        "immutable_digest_promoted": True, "rollback_armed": True,
                        "signoffs": {owner: approval for owner in MODULE.REQUIRED_SIGNOFFS}},
            "post_launch_monitoring": {"continuous_hours": 168, "unresolved_critical_incidents": 0,
                        "slo_dashboard_uri": "https://metrics.example/slo", "incident_log_uri": "s3://evidence/incidents.json",
                        "owner": "operations-owner"},
            "first_week_review": {"completed_at": (datetime.now(timezone.utc) - timedelta(hours=1)).isoformat(),
                        "report_uri": "s3://evidence/week-one.md", "owner": "release-owner", "actions_tracked": True},
            "continuous_improvement": {"cadence_days": 30, "backlog_uri": "https://tracker.example/backlog",
                        "next_review_at": (datetime.now(timezone.utc) + timedelta(days=30)).isoformat(), "owner": "product-owner"}
        }

    def test_complete_launch_operating_evidence_passes(self):
        self.assertEqual([], MODULE.validate(self.valid()))

    def test_missing_signoff_and_week_history_fail(self):
        document = self.valid()
        document["cutover"]["signoffs"]["security_owner"] = {"name": "TBD", "approved_at": "TBD"}
        document["post_launch_monitoring"]["continuous_hours"] = 167
        document["first_week_review"]["actions_tracked"] = False
        document["continuous_improvement"]["cadence_days"] = 91
        errors = MODULE.validate(document)
        self.assertTrue(any("security_owner" in error for error in errors))
        self.assertTrue(any("168" in error for error in errors))
        self.assertTrue(any("actions_tracked" in error for error in errors))
        self.assertTrue(any("between 1 and 90" in error for error in errors))


if __name__ == "__main__":
    unittest.main()

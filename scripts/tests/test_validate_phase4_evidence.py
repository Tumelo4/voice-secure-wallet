import importlib.util
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

PATH = Path(__file__).parents[1] / "validate-phase4-evidence.py"
SPEC = importlib.util.spec_from_file_location("phase4", PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class Phase4EvidenceTests(unittest.TestCase):
    def valid(self):
        common = {"run_id": "run-456", "completed_at": (datetime.now(timezone.utc) - timedelta(days=1)).isoformat(),
                  "evidence_uri": "s3://approved-evidence/run-456.json", "approver": "release-owner", "passed": True}
        return {"runs": [
            {**common, "type": "chaos"}, {**common, "type": "security_scan"},
            {**common, "type": "penetration_test", "independent_tester": True, "open_critical_findings": 0},
            {**common, "type": "voice_shadow_mode", "authorised_transactions": 0},
            {**common, "type": "load_test", "load_multiplier": 10, "p99_within_slo": True},
            {**common, "type": "staging_stint", "continuous_hours": 48, "slo_breaches": 0},
            {**common, "type": "cutover_drill", "rollback_executed": True, "rollback_minutes": 7}
        ]}

    def test_complete_measured_evidence_passes(self):
        self.assertEqual([], MODULE.validate(self.valid()))

    def test_unmeasured_launch_claims_fail(self):
        document = self.valid()
        document["runs"][2]["open_critical_findings"] = 1
        document["runs"][4]["load_multiplier"] = 9
        document["runs"][5]["continuous_hours"] = 47
        document["runs"][6]["rollback_executed"] = False
        errors = MODULE.validate(document)
        self.assertTrue(any("critical" in error for error in errors))
        self.assertTrue(any("at least 10" in error for error in errors))
        self.assertTrue(any("at least 48" in error for error in errors))
        self.assertTrue(any("rollback" in error for error in errors))


if __name__ == "__main__":
    unittest.main()

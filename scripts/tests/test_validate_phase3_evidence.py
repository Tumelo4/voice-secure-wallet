import importlib.util
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

PATH = Path(__file__).parents[1] / "validate-phase3-evidence.py"
SPEC = importlib.util.spec_from_file_location("phase3", PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class Phase3EvidenceTests(unittest.TestCase):
    def valid(self):
        completed = (datetime.now(timezone.utc) - timedelta(days=1)).isoformat()
        common = {"run_id": "run-123", "completed_at": completed,
                  "evidence_uri": "s3://approved-evidence/run-123.json", "approver": "named-owner", "passed": True}
        return {"environment": "staging", "controls": [
            {**common, "type": "telemetry_slo", "observation_hours": 48},
            {**common, "type": "disaster_recovery", "target_rto_minutes": 60, "measured_rto_minutes": 45,
             "target_rpo_minutes": 5, "measured_rpo_minutes": 2},
            {**common, "type": "production_ingress", "tls13": True, "mtls": True,
             "oidc_jwks": True, "waf": True},
            {**common, "type": "pci_dss", "independent_assessor": True},
            {**common, "type": "biometric_evaluation", "independent_assessor": True},
        ]}

    def test_complete_independent_evidence_passes(self):
        self.assertEqual([], MODULE.validate(self.valid()))

    def test_policy_documents_and_placeholders_fail(self):
        document = self.valid()
        document["controls"][0]["observation_hours"] = 24
        document["controls"][1]["measured_rto_minutes"] = 90
        document["controls"][3]["approver"] = "TBD"
        document["controls"][4]["independent_assessor"] = False
        errors = MODULE.validate(document)
        self.assertTrue(any("48" in error for error in errors))
        self.assertTrue(any("RTO" in error for error in errors))
        self.assertTrue(any("real evidence" in error for error in errors))
        self.assertTrue(any("biometric_evaluation" in error for error in errors))


if __name__ == "__main__":
    unittest.main()

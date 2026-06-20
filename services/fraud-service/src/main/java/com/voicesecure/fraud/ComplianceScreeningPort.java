package com.voicesecure.fraud;

import com.voicesecure.compliance.ComplianceProfile;
import com.voicesecure.compliance.ComplianceScreeningResult;

public interface ComplianceScreeningPort {
    ComplianceScreeningResult screen(ComplianceProfile profile);
}

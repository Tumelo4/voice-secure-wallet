package com.voicesecure.fraud;

import com.voicesecure.compliance.ComplianceProfile;
import com.voicesecure.compliance.ComplianceScreeningResult;
import com.voicesecure.compliance.ComplianceService;
import java.util.Objects;

public final class ComplianceServiceScreeningPort implements ComplianceScreeningPort {
    private final ComplianceService complianceService;

    public ComplianceServiceScreeningPort(ComplianceService complianceService) {
        this.complianceService = Objects.requireNonNull(complianceService, "complianceService");
    }

    @Override
    public ComplianceScreeningResult screen(ComplianceProfile profile) {
        return complianceService.screen(profile);
    }
}

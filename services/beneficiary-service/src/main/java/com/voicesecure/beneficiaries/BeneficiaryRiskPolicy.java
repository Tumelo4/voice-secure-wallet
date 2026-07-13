package com.voicesecure.beneficiaries;

import java.time.Duration;
import java.util.UUID;

@FunctionalInterface
public interface BeneficiaryRiskPolicy {
    Duration coolingOffPeriod(UUID customerId, UUID destinationAccountId);

    static BeneficiaryRiskPolicy standard() {
        return (customerId, destinationAccountId) -> Duration.ofHours(24);
    }
}

package com.voicesecure.compliance;

import java.util.Objects;
import java.util.UUID;

public record ComplianceProfile(
        UUID userId,
        String fullName,
        String nationalId,
        String countryCode,
        long transactionAmount
) {
    public ComplianceProfile {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(nationalId, "nationalId");
        Objects.requireNonNull(countryCode, "countryCode");
        if (transactionAmount < 0) {
            throw new ComplianceException("transaction amount cannot be negative");
        }
    }
}


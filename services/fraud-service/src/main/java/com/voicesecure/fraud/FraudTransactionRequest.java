package com.voicesecure.fraud;

import com.voicesecure.compliance.ComplianceProfile;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FraudTransactionRequest(
        UUID transactionId,
        ComplianceProfile complianceProfile,
        long amount,
        String currency,
        UUID deviceId,
        double deviceTrustScore,
        long deviceAgeHours,
        long highValueThreshold,
        Instant occurredAt
) {
    public FraudTransactionRequest {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(complianceProfile, "complianceProfile");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (amount <= 0) {
            throw new FraudException("amount must be positive");
        }
        if (deviceTrustScore < 0.0 || deviceTrustScore > 1.0) {
            throw new FraudException("device trust score must be between 0 and 1");
        }
        if (deviceAgeHours < 0) {
            throw new FraudException("device age cannot be negative");
        }
    }
}


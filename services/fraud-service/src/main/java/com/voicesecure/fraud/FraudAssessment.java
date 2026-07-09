package com.voicesecure.fraud;

import com.voicesecure.compliance.ComplianceScreeningResult;
import java.util.Objects;

public record FraudAssessment(
        boolean approved,
        double score,
        AuthPolicy authPolicy,
        double voiceThreshold,
        String reason,
        int velocityCount,
        double deviceTrustScore,
        ComplianceScreeningResult complianceResult
) {
    public FraudAssessment {
        Objects.requireNonNull(authPolicy, "authPolicy");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(complianceResult, "complianceResult");
        if (Double.isNaN(score) || score < 0.0 || score > 1.0) {
            throw new FraudException("fraud score must be between 0 and 1");
        }
        if (Double.isNaN(voiceThreshold) || voiceThreshold < 0.0 || voiceThreshold > 1.0) {
            throw new FraudException("voice threshold must be between 0 and 1");
        }
        if (Double.isNaN(deviceTrustScore) || deviceTrustScore < 0.0 || deviceTrustScore > 1.0) {
            throw new FraudException("device trust score must be between 0 and 1");
        }
    }
}


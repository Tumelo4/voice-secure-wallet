package com.voicesecure.fraud;

import com.voicesecure.compliance.ComplianceHitType;
import com.voicesecure.compliance.ComplianceProfile;
import com.voicesecure.compliance.ComplianceScreeningResult;
import com.voicesecure.compliance.ComplianceService;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class FraudService {
    private final ComplianceService complianceService;
    private final VelocityTracker velocityTracker;

    public FraudService(ComplianceService complianceService, VelocityTracker velocityTracker) {
        this.complianceService = Objects.requireNonNull(complianceService, "complianceService");
        this.velocityTracker = Objects.requireNonNull(velocityTracker, "velocityTracker");
    }

    public FraudAssessment evaluate(FraudTransactionRequest request) {
        ComplianceScreeningResult compliance = complianceService.screen(request.complianceProfile());
        int velocity = velocityTracker.recordAndCount(request.complianceProfile().userId(), request.occurredAt(), Duration.ofMinutes(5));
        double score = score(request, compliance, velocity);
        boolean approved = compliance.hitType() == ComplianceHitType.NONE && score < 0.90;
        AuthPolicy authPolicy = choosePolicy(request, score, compliance);
        double voiceThreshold = Math.min(0.95, Math.max(0.55, 0.90 - score / 2.0));
        String reason = approved ? "approved" : compliance.hit() ? compliance.reason() : "risk score too high";
        if (compliance.hit()) {
            score = 1.0;
            authPolicy = AuthPolicy.DEVICE_PIN;
            voiceThreshold = 0.95;
            reason = compliance.reason();
        }
        return new FraudAssessment(approved, score, authPolicy, voiceThreshold, reason, velocity, request.deviceTrustScore(), compliance);
    }

    private double score(FraudTransactionRequest request, ComplianceScreeningResult compliance, int velocity) {
        double amountRisk = Math.min(0.45, request.amount() / 50000.0);
        double velocityRisk = Math.min(0.25, Math.max(0, velocity - 3) * 0.05);
        double deviceRisk = Math.min(0.35, (1.0 - request.deviceTrustScore()) * 0.4);
        double agePenalty = request.deviceAgeHours() < 24 ? 0.10 : 0.0;
        double compliancePenalty = compliance.hit() ? 0.50 : 0.0;
        return Math.min(1.0, 0.05 + amountRisk + velocityRisk + deviceRisk + agePenalty + compliancePenalty);
    }

    private AuthPolicy choosePolicy(FraudTransactionRequest request, double score, ComplianceScreeningResult compliance) {
        if (compliance.hit()) {
            return AuthPolicy.DEVICE_PIN;
        }
        if (request.amount() > request.highValueThreshold()) {
            return AuthPolicy.VOICE_OTP;
        }
        if (score >= 0.65 || request.deviceTrustScore() < 0.4) {
            return AuthPolicy.DEVICE_PIN;
        }
        if (score >= 0.25) {
            return AuthPolicy.VOICE_OTP;
        }
        return AuthPolicy.VOICE_ONLY;
    }
}


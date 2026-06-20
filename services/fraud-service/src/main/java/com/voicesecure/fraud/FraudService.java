package com.voicesecure.fraud;

import com.voicesecure.compliance.ComplianceHitType;
import com.voicesecure.compliance.ComplianceScreeningResult;
import com.voicesecure.compliance.ComplianceService;
import java.util.Objects;

public final class FraudService {
    private final ComplianceScreeningPort complianceScreeningPort;
    private final VelocityTracker velocityTracker;
    private final FraudPolicy policy;

    public FraudService(ComplianceService complianceService, VelocityTracker velocityTracker) {
        this(new ComplianceServiceScreeningPort(complianceService), velocityTracker, FraudPolicy.defaults());
    }

    public FraudService(ComplianceScreeningPort complianceScreeningPort, VelocityTracker velocityTracker, FraudPolicy policy) {
        this.complianceScreeningPort = Objects.requireNonNull(complianceScreeningPort, "complianceScreeningPort");
        this.velocityTracker = Objects.requireNonNull(velocityTracker, "velocityTracker");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public FraudAssessment evaluate(FraudTransactionRequest request) {
        ComplianceScreeningResult compliance = complianceScreeningPort.screen(request.complianceProfile());
        int velocity = velocityTracker.recordAndCount(request.complianceProfile().userId(), request.occurredAt(), policy.velocityWindow());
        double score = score(request, compliance, velocity);
        boolean approved = compliance.hitType() == ComplianceHitType.NONE && score < policy.approvalScoreThreshold();
        AuthPolicy authPolicy = choosePolicy(request, score, compliance);
        double voiceThreshold = Math.min(
                policy.maxVoiceThreshold(),
                Math.max(policy.minVoiceThreshold(), policy.voiceThresholdBase() - score / 2.0)
        );
        String reason = approved ? "approved" : compliance.hit() ? compliance.reason() : "risk score too high";
        if (compliance.hit()) {
            score = 1.0;
            authPolicy = AuthPolicy.DEVICE_PIN;
            voiceThreshold = policy.maxVoiceThreshold();
            reason = compliance.reason();
        }
        return new FraudAssessment(approved, score, authPolicy, voiceThreshold, reason, velocity, request.deviceTrustScore(), compliance);
    }

    private double score(FraudTransactionRequest request, ComplianceScreeningResult compliance, int velocity) {
        double amountRisk = Math.min(policy.amountRiskLimit(), request.amount() / policy.amountRiskDivisor());
        double velocityRisk = Math.min(
                policy.velocityRiskLimit(),
                Math.max(0, velocity - policy.includedVelocityEvents()) * policy.velocityRiskStep()
        );
        double deviceRisk = Math.min(policy.deviceRiskLimit(), (1.0 - request.deviceTrustScore()) * policy.deviceRiskMultiplier());
        double agePenalty = request.deviceAgeHours() < policy.newDeviceAgeHours() ? policy.newDevicePenalty() : 0.0;
        double compliancePenalty = compliance.hit() ? policy.compliancePenalty() : 0.0;
        return Math.min(1.0, policy.baseScore() + amountRisk + velocityRisk + deviceRisk + agePenalty + compliancePenalty);
    }

    private AuthPolicy choosePolicy(FraudTransactionRequest request, double score, ComplianceScreeningResult compliance) {
        if (compliance.hit()) {
            return AuthPolicy.DEVICE_PIN;
        }
        if (request.amount() > request.highValueThreshold()) {
            return AuthPolicy.VOICE_OTP;
        }
        if (score >= policy.devicePinScoreThreshold() || request.deviceTrustScore() < policy.lowTrustDeviceThreshold()) {
            return AuthPolicy.DEVICE_PIN;
        }
        if (score >= policy.voiceOtpScoreThreshold()) {
            return AuthPolicy.VOICE_OTP;
        }
        return AuthPolicy.VOICE_ONLY;
    }
}

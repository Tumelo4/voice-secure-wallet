package com.voicesecure.launch;

import java.util.List;

final class ProductionCutoverLaunchGate implements LaunchGate {
    @Override
    public void validate(LaunchReadinessPlan plan, LaunchReadinessPolicy policy, List<String> blockers) {
        ProductionCutoverPlan cutoverPlan = plan.productionCutoverPlan();
        if (cutoverPlan.changeTicketId().isBlank()) {
            blockers.add("production change ticket must be linked");
        }
        if (!cutoverPlan.rollbackPlanTested()) {
            blockers.add("rollback plan must be tested before cutover");
        }
        if (!cutoverPlan.featureFlagsLocked()) {
            blockers.add("feature flags must be locked before cutover");
        }
        if (!cutoverPlan.productionMonitoringArmed()) {
            blockers.add("production monitoring and alerts must be armed");
        }
        if (!cutoverPlan.onCallConfirmed()) {
            blockers.add("primary and secondary on-call must be confirmed");
        }
        if (!cutoverPlan.supportBriefed()) {
            blockers.add("support team must be briefed before launch");
        }
        if (cutoverPlan.rollbackTimeMinutes() <= 0
                || cutoverPlan.rollbackTimeMinutes() > policy.maximumRollbackMinutes()) {
            blockers.add("rollback must be executable within " + policy.maximumRollbackMinutes() + " minutes");
        }
    }
}

package com.voicesecure.launch;

import java.util.List;

final class CoreLaunchGate implements LaunchGate {
    @Override
    public void validate(LaunchReadinessPlan plan, LaunchReadinessPolicy policy, List<String> blockers) {
        if (!plan.reconciliationRunPassed()) {
            blockers.add("48-hour reconciliation run must pass");
        }
        if (!plan.drRestoreAndRepairPassed()) {
            blockers.add("DR restore and repair gate must pass");
        }
        if (!plan.sloGreenFor48Hours()) {
            blockers.add("SLOs must be green for 48 hours");
        }
        if (!plan.runbooksWritten()) {
            blockers.add("all Tier 1 runbooks must be written");
        }
        if (!plan.contractTestsPassed()) {
            blockers.add("contract tests must pass");
        }
        if (!plan.migrationComplete()) {
            blockers.add("historical migration must be complete");
        }
        if (!plan.adminRepairDrillPassed()) {
            blockers.add("admin repair drill must pass");
        }
        if (!plan.deviceBindingPassed()) {
            blockers.add("device binding must pass");
        }
        if (!plan.complianceAuditPassed()) {
            blockers.add("compliance audit must pass");
        }
        if (!plan.documentationComplete()) {
            blockers.add("documentation must be complete");
        }
    }
}

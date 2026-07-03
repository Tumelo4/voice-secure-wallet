package com.voicesecure.ops;

import java.util.List;
import java.util.Objects;

public record DurableInfrastructureValidationReport(boolean ready, List<String> blockers) {
    public DurableInfrastructureValidationReport {
        Objects.requireNonNull(blockers, "blockers");
        blockers = List.copyOf(blockers);
        if (ready != blockers.isEmpty()) {
            throw new OpsException("ready must match blocker state");
        }
    }
}

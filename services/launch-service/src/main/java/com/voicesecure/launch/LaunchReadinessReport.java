package com.voicesecure.launch;

import java.util.List;
import java.util.Objects;

public record LaunchReadinessReport(
        boolean ready,
        List<String> blockers
) {
    public LaunchReadinessReport {
        Objects.requireNonNull(blockers, "blockers");
        blockers = List.copyOf(blockers);
    }
}

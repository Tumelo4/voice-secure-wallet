package com.voicesecure.ops;

import java.util.List;
import java.util.Objects;

public record DeploymentPipelineSpec(
        List<DeploymentStage> stages,
        boolean manualApprovalRequired,
        boolean blueGreenEnabled,
        boolean canaryEnabled
) {
    public DeploymentPipelineSpec {
        Objects.requireNonNull(stages, "stages");
        stages = List.copyOf(stages);
    }
}

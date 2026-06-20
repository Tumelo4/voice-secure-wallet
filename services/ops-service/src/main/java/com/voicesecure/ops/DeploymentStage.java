package com.voicesecure.ops;

public enum DeploymentStage {
    BUILD_TEST,
    CONTAINER_BUILD,
    INTEGRATION_TESTS,
    DEPLOY_STAGING,
    DEPLOY_PRODUCTION
}

package com.voicesecure.launch;

public enum ChaosScenario {
    AWS_FIS_DEPENDENCY_FAULT,
    AWS_FIS_LATENCY_SPIKE,
    TOXIPROXY_VOICE_OUTAGE,
    TOXIPROXY_KAFKA_PARTITION,
    REGION_FAILOVER
}

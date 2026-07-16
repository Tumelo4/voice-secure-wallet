package com.voicesecure.api;

import java.util.HashMap;
import java.util.Map;

public final class ProductionConfigurationTests {
    public static void main(String[] args) {
        Map<String, String> valid = new HashMap<>(Map.ofEntries(
                Map.entry("DATABASE_URL", "jdbc:postgresql://db.internal/wallet"), Map.entry("DATABASE_USER", "wallet"),
                Map.entry("DATABASE_PASSWORD", "secret"), Map.entry("REDIS_URI", "rediss://cache.internal:6379"),
                Map.entry("KAFKA_BOOTSTRAP_SERVERS", "broker.internal:9098"), Map.entry("OIDC_ISSUER", "https://identity.example"),
                Map.entry("OIDC_JWKS_URI", "https://identity.example/.well-known/jwks.json"), Map.entry("OIDC_AUDIENCE", "wallet-api"),
                Map.entry("BENEFICIARY_DIRECTORY_URI", "https://accounts.internal"),
                Map.entry("FRAUD_SERVICE_URI", "https://fraud.internal"), Map.entry("VOICE_SERVICE_URI", "https://voice.internal")));
        ProductionConfiguration configuration = ProductionConfiguration.fromEnvironment(valid);
        assertEquals(2000L, configuration.remoteTimeout().toMillis(), "default timeout");

        Map<String, String> insecureRedis = new HashMap<>(valid); insecureRedis.put("REDIS_URI", "redis://cache:6379");
        assertRejects(insecureRedis, "production Redis without TLS");
        Map<String, String> missingAudience = new HashMap<>(valid); missingAudience.remove("OIDC_AUDIENCE");
        assertRejects(missingAudience, "missing audience");
        Map<String, String> insecureVoice = new HashMap<>(valid); insecureVoice.put("VOICE_SERVICE_URI", "http://voice");
        assertRejects(insecureVoice, "insecure voice boundary");
        Map<String, String> excessiveTimeout = new HashMap<>(valid); excessiveTimeout.put("REMOTE_TIMEOUT_MS", "30001");
        assertRejects(excessiveTimeout, "unbounded timeout");
        System.out.println("Production configuration tests passed: 5");
    }
    private static void assertRejects(Map<String, String> values, String message) {
        try { ProductionConfiguration.fromEnvironment(values); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { }
    }
    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + " but got " + actual);
    }
}

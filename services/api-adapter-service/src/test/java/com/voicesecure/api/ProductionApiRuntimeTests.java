package com.voicesecure.api;

import java.util.HashMap;
import java.util.Map;

public final class ProductionApiRuntimeTests {
    public static void main(String[] args) {
        Map<String, String> valid = valid();
        ProductionConfiguration.fromEnvironment(valid);
        ProductionApiRuntime.validateEnvironment(valid);
        assertRejects(with(valid, "DATABASE_POOL_SIZE", "1"), "undersized pool");
        assertRejects(with(valid, "KAFKA_SECURITY_PROTOCOL", "PLAINTEXT"), "insecure Kafka");
        Map<String, String> missingVoiceToken = new HashMap<>(valid); missingVoiceToken.remove("VOICE_SERVICE_TOKEN");
        assertRejects(missingVoiceToken, "missing service token");
        Map<String, String> missingCallback = new HashMap<>(valid); missingCallback.remove("KAFKA_SASL_CALLBACK_HANDLER_CLASS");
        assertRejects(missingCallback, "missing SASL callback");
        System.out.println("Production API runtime tests passed: 5");
    }

    static Map<String, String> valid() {
        Map<String, String> values = new HashMap<>();
        values.put("DATABASE_URL", "jdbc:postgresql://db.internal/wallet"); values.put("DATABASE_USER", "wallet");
        values.put("DATABASE_PASSWORD", "secret"); values.put("DATABASE_POOL_SIZE", "20");
        values.put("REDIS_URI", "rediss://cache.internal:6379"); values.put("KAFKA_BOOTSTRAP_SERVERS", "broker.internal:9098");
        values.put("KAFKA_SECURITY_PROTOCOL", "SASL_SSL"); values.put("KAFKA_SASL_MECHANISM", "AWS_MSK_IAM");
        values.put("KAFKA_SASL_CALLBACK_HANDLER_CLASS", "software.amazon.msk.auth.iam.IAMClientCallbackHandler");
        values.put("OIDC_ISSUER", "https://identity.example"); values.put("OIDC_JWKS_URI", "https://identity.example/jwks");
        values.put("OIDC_AUDIENCE", "wallet-api"); values.put("BENEFICIARY_DIRECTORY_URI", "https://accounts.internal");
        values.put("FRAUD_SERVICE_URI", "https://fraud.internal"); values.put("VOICE_SERVICE_URI", "https://voice.internal");
        values.put("BENEFICIARY_DIRECTORY_TOKEN", "accounts-token"); values.put("FRAUD_SERVICE_TOKEN", "fraud-token");
        values.put("VOICE_SERVICE_TOKEN", "voice-token");
        return values;
    }
    private static Map<String, String> with(Map<String, String> source, String key, String value) {
        Map<String, String> result = new HashMap<>(source); result.put(key, value); return result;
    }
    private static void assertRejects(Map<String, String> environment, String label) {
        try { ProductionApiRuntime.validateEnvironment(environment); throw new AssertionError(label); }
        catch (IllegalArgumentException expected) { }
    }
}

package com.voicesecure.api;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public record ProductionConfiguration(
        String databaseUrl,
        String databaseUser,
        String databasePassword,
        URI redisUri,
        String kafkaBootstrapServers,
        URI oidcIssuer,
        URI oidcJwksUri,
        String oidcAudience,
        URI fraudServiceUri,
        URI voiceServiceUri,
        Duration remoteTimeout
) {
    public ProductionConfiguration {
        requirePrefix(databaseUrl, "jdbc:postgresql://", "DATABASE_URL");
        requireText(databaseUser, "DATABASE_USER"); requireText(databasePassword, "DATABASE_PASSWORD");
        requireScheme(redisUri, "rediss", "REDIS_URI");
        requireText(kafkaBootstrapServers, "KAFKA_BOOTSTRAP_SERVERS");
        requireScheme(oidcIssuer, "https", "OIDC_ISSUER"); requireScheme(oidcJwksUri, "https", "OIDC_JWKS_URI");
        requireText(oidcAudience, "OIDC_AUDIENCE");
        requireScheme(fraudServiceUri, "https", "FRAUD_SERVICE_URI");
        requireScheme(voiceServiceUri, "https", "VOICE_SERVICE_URI");
        Objects.requireNonNull(remoteTimeout, "remoteTimeout");
        if (remoteTimeout.isZero() || remoteTimeout.isNegative() || remoteTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("REMOTE_TIMEOUT_MS must be between 1 and 30000");
        }
    }

    public static ProductionConfiguration fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        return new ProductionConfiguration(required(environment, "DATABASE_URL"), required(environment, "DATABASE_USER"),
                required(environment, "DATABASE_PASSWORD"), uri(environment, "REDIS_URI"),
                required(environment, "KAFKA_BOOTSTRAP_SERVERS"), uri(environment, "OIDC_ISSUER"),
                uri(environment, "OIDC_JWKS_URI"), required(environment, "OIDC_AUDIENCE"),
                uri(environment, "FRAUD_SERVICE_URI"), uri(environment, "VOICE_SERVICE_URI"),
                Duration.ofMillis(positiveLong(environment.getOrDefault("REMOTE_TIMEOUT_MS", "2000"), "REMOTE_TIMEOUT_MS")));
    }

    private static URI uri(Map<String, String> environment, String name) {
        try { return URI.create(required(environment, name)); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(name + " must be a valid URI", invalid); }
    }
    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name); requireText(value, name); return value.trim();
    }
    private static long positiveLong(String value, String name) {
        try { long parsed = Long.parseLong(value); if (parsed <= 0) throw new NumberFormatException(); return parsed; }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException(name + " must be positive", invalid); }
    }
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
    private static void requirePrefix(String value, String prefix, String name) {
        requireText(value, name); if (!value.startsWith(prefix)) throw new IllegalArgumentException(name + " must use " + prefix);
    }
    private static void requireScheme(URI value, String scheme, String name) {
        Objects.requireNonNull(value, name); if (!scheme.equalsIgnoreCase(value.getScheme()) || value.getHost() == null) {
            throw new IllegalArgumentException(name + " must use " + scheme + "://");
        }
    }
}

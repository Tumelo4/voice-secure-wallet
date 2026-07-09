package com.voicesecure.api;

import com.voicesecure.identity.IdentityService;
import com.voicesecure.identity.InMemoryIdentityRepository;
import com.voicesecure.identity.SessionGrant;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.UUID;

public final class IdentityBearerTokenVerifierTests {
    public static void main(String[] args) throws Exception {
        TestCase[] tests = {
                new TestCase("identity JWT verifier maps claims into API principals", IdentityBearerTokenVerifierTests::mapsClaimsIntoPrincipals),
                new TestCase("identity JWT verifier rejects invalid tokens", IdentityBearerTokenVerifierTests::rejectsInvalidTokens)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Identity bearer token verifier tests passed: " + tests.length);
    }

    private static void mapsClaimsIntoPrincipals() throws Exception {
        Fixture fixture = fixture();
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        fixture.identityService.registerDevice(userId, deviceId, fixture.deviceKeys.getPublic());

        SessionGrant grant = fixture.identityService.createSession(
                userId,
                deviceId,
                "wallet:payment wallet:balance",
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        );

        ApiPrincipal principal = fixture.verifier.verify(grant.accessToken().token()).orElseThrow();

        assertEquals(userId.toString(), principal.principalId(), "principal id");
        assertTrue(principal.hasScope("wallet:payment"), "payment scope");
        assertTrue(principal.hasScope("wallet:balance"), "balance scope");
        assertEquals(2, principal.scopes().size(), "scope count");
    }

    private static void rejectsInvalidTokens() throws Exception {
        Fixture fixture = fixture();

        assertTrue(fixture.verifier.verify("not-a-jwt").isEmpty(), "invalid token should be rejected");
    }

    private static Fixture fixture() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair signingKey = generator.generateKeyPair();
        KeyPair deviceKeys = generator.generateKeyPair();
        IdentityService identityService = new IdentityService(new InMemoryIdentityRepository(), signingKey, "voice-secure-key-1");
        return new Fixture(identityService, new IdentityBearerTokenVerifier(identityService), deviceKeys);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private record Fixture(IdentityService identityService, IdentityBearerTokenVerifier verifier, KeyPair deviceKeys) {
    }

    private record TestCase(String name, ThrowingRunnable runnable) {
        void run() throws Exception {
            runnable.run();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

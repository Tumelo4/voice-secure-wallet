package com.voicesecure.identity;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.util.UUID;

public final class IdentityServiceTests {
    public static void main(String[] args) throws Exception {
        TestCase[] tests = {
                new TestCase("issues and verifies RS256 access tokens", IdentityServiceTests::issuesAndVerifiesAccessTokens),
                new TestCase("device signatures are validated on critical requests", IdentityServiceTests::validatesCriticalRequestSignatures),
                new TestCase("refresh token reuse revokes the family", IdentityServiceTests::refreshTokenReuseRevokesFamily),
                new TestCase("device certificates can be reissued", IdentityServiceTests::reissuesDeviceCertificates)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Identity service tests passed: " + tests.length);
    }

    private static void issuesAndVerifiesAccessTokens() throws Exception {
        Fixture fixture = fixture();
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        fixture.service.registerDevice(userId, deviceId, fixture.deviceKeys.getPublic());

        SessionGrant grant = fixture.service.createSession(userId, deviceId, "wallet:payment", Duration.ofMinutes(15), Duration.ofDays(7));
        AccessTokenClaims verified = fixture.service.verifyAccessToken(grant.accessToken().token());

        assertEquals(userId, verified.subjectUserId(), "user id");
        assertEquals(deviceId, verified.deviceId(), "device id");
        assertEquals(grant.familyState().familyId(), verified.familyId(), "family id");
        assertEquals("wallet:payment", verified.scope(), "scope");
        assertTrue(!fixture.service.jwks().keys().isEmpty(), "jwks should contain the signing key");
    }

    private static void validatesCriticalRequestSignatures() throws Exception {
        Fixture fixture = fixture();
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        fixture.service.registerDevice(userId, deviceId, fixture.deviceKeys.getPublic());

        byte[] payload = "payment-initiation:12345".getBytes(StandardCharsets.UTF_8);
        byte[] signature = sign(fixture.deviceKeys.getPrivate(), payload);
        boolean verified = fixture.service.validateCriticalRequest(userId, deviceId, payload, signature);

        assertTrue(verified, "device signature should verify");
    }

    private static void refreshTokenReuseRevokesFamily() throws Exception {
        Fixture fixture = fixture();
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        fixture.service.registerDevice(userId, deviceId, fixture.deviceKeys.getPublic());

        SessionGrant grant = fixture.service.createSession(userId, deviceId, "wallet:payment", Duration.ofMinutes(15), Duration.ofDays(7));
        SessionGrant rotated = fixture.service.rotateRefreshToken(grant.familyState().familyId(), grant.refreshToken(), Duration.ofDays(7));

        assertTrue(!rotated.refreshToken().equals(grant.refreshToken()), "rotation should issue a new refresh token");
        assertThrows(IdentityException.class, () -> fixture.service.rotateRefreshToken(grant.familyState().familyId(), grant.refreshToken(), Duration.ofDays(7)), "reused token should revoke the family");
    }

    private static void reissuesDeviceCertificates() throws Exception {
        Fixture fixture = fixture();
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        fixture.service.registerDevice(userId, deviceId, fixture.deviceKeys.getPublic());

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair replacementDeviceKey = generator.generateKeyPair();
        DeviceRegistration updated = fixture.service.reissueDeviceCertificate(userId, deviceId, replacementDeviceKey.getPublic());

        assertEquals(userId, updated.userId(), "user id");
        assertEquals(deviceId, updated.deviceId(), "device id");
        assertTrue(!fixture.deviceKeys.getPublic().equals(updated.publicKey()), "reissue should replace the device certificate");
        assertTrue(updated.active(), "reissued certificate should remain active");
    }

    private static Fixture fixture() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair signingKey = generator.generateKeyPair();
        KeyPair deviceKey = generator.generateKeyPair();
        return new Fixture(new IdentityService(new InMemoryIdentityRepository(), signingKey, "voice-secure-key-1"), deviceKey);
    }

    private static byte[] sign(PrivateKey privateKey, byte[] payload) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(payload);
        return signature.sign();
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

    private static void assertThrows(Class<? extends Throwable> expected, ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return;
            }
            throw new AssertionError(message + ": expected " + expected.getSimpleName() + " but got " + actual, actual);
        }
        throw new AssertionError(message + ": expected " + expected.getSimpleName());
    }

    private record Fixture(IdentityService service, KeyPair deviceKeys) {
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

package com.voicesecure.identity;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class JwtCodecTests {
    public static void main(String[] args) throws Exception {
        TestCase[] tests = {
                new TestCase("round trips complex unicode claims", JwtCodecTests::roundTripsComplexClaims),
                new TestCase("rejects malformed compact tokens cleanly", JwtCodecTests::rejectsMalformedTokens),
                new TestCase("accepts prior key during rotation overlap", JwtCodecTests::acceptsPreviousKeyDuringOverlap),
                new TestCase("rejects retired keys and publishes overlap JWKS", JwtCodecTests::rejectsRetiredKeys)
        };
        for (TestCase test : tests) {
            test.runnable.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("JWT codec tests passed: " + tests.length);
    }

    private static void roundTripsComplexClaims() throws Exception {
        JwtCodec codec = new JwtCodec(keyPair(), "current-key");
        AccessTokenClaims claims = claims("wallet:pay,beneficiary:\"Müller 東京\"");
        AccessTokenClaims verified = codec.verify(codec.issue(claims).token());
        assertEquals(claims.scope(), verified.scope(), "complex scope");
        assertEquals(claims.subjectUserId(), verified.subjectUserId(), "subject");
    }

    private static void rejectsMalformedTokens() throws Exception {
        JwtCodec codec = new JwtCodec(keyPair(), "current-key");
        String valid = codec.issue(claims("wallet:pay")).token();
        assertThrows(() -> codec.verify("%%%.."), "malformed base64");
        assertThrows(() -> codec.verify(valid.substring(0, valid.lastIndexOf('.'))), "truncated token");
        assertThrows(() -> codec.verify(valid + ".extra"), "extra segment");
    }

    private static void acceptsPreviousKeyDuringOverlap() throws Exception {
        KeyPair previous = keyPair();
        KeyPair current = keyPair();
        JwtCodec previousCodec = new JwtCodec(previous, "previous-key");
        String oldToken = previousCodec.issue(claims("wallet:pay")).token();
        JwtCodec rotatingCodec = new JwtCodec(
                current,
                "current-key",
                Map.of("previous-key", previous.getPublic(), "current-key", current.getPublic()));
        rotatingCodec.verify(oldToken);
        assertEquals(2, rotatingCodec.publicJwks().size(), "rotation JWKS key count");
    }

    private static void rejectsRetiredKeys() throws Exception {
        KeyPair previous = keyPair();
        KeyPair current = keyPair();
        String oldToken = new JwtCodec(previous, "previous-key").issue(claims("wallet:pay")).token();
        JwtCodec retiredCodec = new JwtCodec(current, "current-key", Map.of("current-key", current.getPublic()));
        assertThrows(() -> retiredCodec.verify(oldToken), "retired key");
    }

    private static AccessTokenClaims claims(String scope) {
        Instant now = Instant.now();
        return new AccessTokenClaims(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), scope,
                UUID.randomUUID(), now, now.plusSeconds(900));
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static void assertThrows(ThrowingRunnable runnable, String label) {
        try {
            runnable.run();
        } catch (IdentityException expected) {
            return;
        } catch (Exception exception) {
            throw new AssertionError(label + " leaked unexpected exception", exception);
        }
        throw new AssertionError(label + " should be rejected");
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
        }
    }

    private record TestCase(String name, ThrowingRunnable runnable) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

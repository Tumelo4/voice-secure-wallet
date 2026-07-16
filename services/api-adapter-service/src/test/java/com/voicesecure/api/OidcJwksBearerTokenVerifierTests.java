package com.voicesecure.api;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

public final class OidcJwksBearerTokenVerifierTests {
    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");
    public static void main(String[] args) throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("current").generate();
        OidcJwksBearerTokenVerifier verifier = new OidcJwksBearerTokenVerifier(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(key.toPublicJWK())), URI.create("https://issuer.example"),
                "wallet-api", Clock.fixed(NOW, ZoneOffset.UTC));
        UUID subject = UUID.randomUUID();
        ApiPrincipal principal = verifier.verify(token(key, subject, "wallet-api", NOW.plusSeconds(60))).orElseThrow();
        assertTrue(principal.hasScope("wallet:payment"), "valid signed scope");
        assertTrue(verifier.verify(token(key, subject, "other-api", NOW.plusSeconds(60))).isEmpty(), "wrong audience");
        assertTrue(verifier.verify(token(key, subject, "wallet-api", NOW.minusSeconds(1))).isEmpty(), "expired token");
        assertTrue(verifier.verify("not-a-jwt").isEmpty(), "malformed token");
        System.out.println("OIDC JWKS bearer verifier tests passed: 4");
    }
    private static String token(RSAKey key, UUID subject, String audience, Instant expiry) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("https://issuer.example").audience(audience)
                .subject(subject.toString()).claim("scope", "wallet:payment wallet:balance")
                .issueTime(Date.from(NOW)).expirationTime(Date.from(expiry)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key)); return jwt.serialize();
    }
    private static void assertTrue(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

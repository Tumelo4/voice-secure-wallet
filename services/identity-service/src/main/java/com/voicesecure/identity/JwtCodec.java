package com.voicesecure.identity;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class JwtCodec {
    private final String currentKeyId;
    private final RSAPrivateKey signingKey;
    private final Map<String, RSAPublicKey> verificationKeys;

    JwtCodec(KeyPair keyPair, String keyId) {
        this(keyPair, keyId, Map.of(keyId, keyPair.getPublic()));
    }

    JwtCodec(KeyPair currentKeyPair, String currentKeyId, Map<String, PublicKey> acceptedPublicKeys) {
        if (!(currentKeyPair.getPrivate() instanceof RSAPrivateKey rsaPrivateKey)) {
            throw new IdentityException("RSA private key required for JWT signing");
        }
        this.currentKeyId = requireKeyId(currentKeyId);
        this.signingKey = rsaPrivateKey;
        Map<String, RSAPublicKey> keys = new LinkedHashMap<>();
        acceptedPublicKeys.forEach((kid, key) -> {
            if (!(key instanceof RSAPublicKey rsaPublicKey)) {
                throw new IdentityException("RSA public key required for JWT verification");
            }
            keys.put(requireKeyId(kid), rsaPublicKey);
        });
        keys.put(this.currentKeyId, (RSAPublicKey) currentKeyPair.getPublic());
        this.verificationKeys = Map.copyOf(keys);
    }

    JwtToken issue(AccessTokenClaims claims) {
        try {
            JWTClaimsSet claimSet = new JWTClaimsSet.Builder()
                    .subject(claims.subjectUserId().toString())
                    .claim("device_id", claims.deviceId().toString())
                    .claim("family_id", claims.familyId().toString())
                    .claim("scope", claims.scope())
                    .jwtID(claims.tokenId().toString())
                    .issueTime(Date.from(claims.issuedAt()))
                    .expirationTime(Date.from(claims.expiresAt()))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(currentKeyId).type(com.nimbusds.jose.JOSEObjectType.JWT).build(),
                    claimSet);
            jwt.sign(new RSASSASigner(signingKey));
            return new JwtToken(jwt.serialize(), claims);
        } catch (JOSEException exception) {
            throw new IdentityException("unable to sign access token");
        }
    }

    AccessTokenClaims verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new IdentityException("unsupported token algorithm");
            }
            String keyId = requireKeyId(jwt.getHeader().getKeyID());
            RSAPublicKey verificationKey = verificationKeys.get(keyId);
            if (verificationKey == null) {
                throw new IdentityException("unknown token key id");
            }
            if (!jwt.verify(new RSASSAVerifier(verificationKey))) {
                throw new IdentityException("invalid token signature");
            }
            JWTClaimsSet values = jwt.getJWTClaimsSet();
            AccessTokenClaims claims = new AccessTokenClaims(
                    UUID.fromString(values.getSubject()),
                    UUID.fromString(values.getStringClaim("device_id")),
                    UUID.fromString(values.getStringClaim("family_id")),
                    required(values.getStringClaim("scope"), "scope"),
                    UUID.fromString(values.getJWTID()),
                    requiredDate(values.getIssueTime(), "iat").toInstant(),
                    requiredDate(values.getExpirationTime(), "exp").toInstant());
            if (!Instant.now().isBefore(claims.expiresAt())) {
                throw new IdentityException("access token expired");
            }
            return claims;
        } catch (IdentityException exception) {
            throw exception;
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw new IdentityException("invalid access token");
        }
    }

    List<JsonWebKey> publicJwks() {
        return verificationKeys.entrySet().stream()
                .map(entry -> new JwksKeyFactory(entry.getValue(), entry.getKey()).create())
                .toList();
    }

    static boolean verifySignature(PublicKey publicKey, byte[] payload, byte[] signatureBytes) {
        return DeviceSignatureVerifier.verify(publicKey, payload, signatureBytes);
    }

    private static String requireKeyId(String value) {
        return required(value, "kid");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IdentityException("missing token field: " + name);
        }
        return value;
    }

    private static Date requiredDate(Date value, String name) {
        if (value == null) {
            throw new IdentityException("missing token field: " + name);
        }
        return value;
    }
}

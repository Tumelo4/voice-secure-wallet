package com.voicesecure.identity;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class JwtCodec {
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;

    JwtCodec(KeyPair keyPair, String keyId) {
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        this.keyId = keyId;
    }

    JwtToken issue(AccessTokenClaims claims) {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + escape(keyId) + "\"}";
        String payload = claimsToJson(claims);
        String signingInput = base64Url(header.getBytes(StandardCharsets.UTF_8)) + "." + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        String signature = sign(signingInput);
        return new JwtToken(signingInput + "." + signature, claims);
    }

    AccessTokenClaims verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IdentityException("invalid token format");
        }
        String signingInput = parts[0] + "." + parts[1];
        String actualSignature = parts[2];
        String expectedSignature = sign(signingInput);
        if (!constantTimeEquals(actualSignature, expectedSignature)) {
            throw new IdentityException("invalid token signature");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        AccessTokenClaims claims = parseClaims(payload);
        if (Instant.now().isAfter(claims.expiresAt())) {
            throw new IdentityException("access token expired");
        }
        return claims;
    }

    JsonWebKey publicJwk() {
        if (!(publicKey instanceof RSAPublicKey rsaPublicKey)) {
            throw new IdentityException("RSA public key required for JWKS");
        }
        BigInteger modulus = rsaPublicKey.getModulus();
        BigInteger exponent = rsaPublicKey.getPublicExponent();
        return new JsonWebKey(
                keyId,
                "RSA",
                "RS256",
                "sig",
                base64Url(modulus.toByteArray()),
                base64Url(exponent.toByteArray())
        );
    }

    static boolean verifySignature(PublicKey publicKey, byte[] payload, byte[] signatureBytes) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(payload);
            return signature.verify(signatureBytes);
        } catch (Exception ex) {
            throw new IdentityException("unable to verify device signature: " + ex.getMessage());
        }
    }

    private String sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return base64Url(signature.sign());
        } catch (Exception ex) {
            throw new IdentityException("unable to sign token: " + ex.getMessage());
        }
    }

    private AccessTokenClaims parseClaims(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IdentityException("invalid token payload");
        }
        String body = trimmed.substring(1, trimmed.length() - 1);
        for (String part : body.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = unquote(kv[0]);
            String value = kv[1].trim();
            values.put(key, value);
        }
        return new AccessTokenClaims(
                UUID.fromString(unquote(values.get("sub"))),
                UUID.fromString(unquote(values.get("device_id"))),
                UUID.fromString(unquote(values.get("family_id"))),
                unquote(values.get("scope")),
                UUID.fromString(unquote(values.get("jti"))),
                Instant.parse(unquote(values.get("iat"))),
                Instant.parse(unquote(values.get("exp")))
        );
    }

    private String claimsToJson(AccessTokenClaims claims) {
        return "{"
                + "\"sub\":\"" + escape(claims.subjectUserId().toString()) + "\","
                + "\"device_id\":\"" + escape(claims.deviceId().toString()) + "\","
                + "\"family_id\":\"" + escape(claims.familyId().toString()) + "\","
                + "\"scope\":\"" + escape(claims.scope()) + "\","
                + "\"jti\":\"" + escape(claims.tokenId().toString()) + "\","
                + "\"iat\":\"" + escape(claims.issuedAt().toString()) + "\","
                + "\"exp\":\"" + escape(claims.expiresAt().toString()) + "\""
                + "}";
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unquote(String value) {
        if (value == null) {
            throw new IdentityException("missing claim");
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }
}

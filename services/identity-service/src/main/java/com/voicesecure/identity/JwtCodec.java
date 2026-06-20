package com.voicesecure.identity;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

final class JwtCodec {
    private final String keyId;
    private final JwtClaimsCodec claimsCodec = new JwtClaimsCodec();
    private final JwtSigner signer;
    private final JwtVerifier verifier;
    private final JwksKeyFactory jwksKeyFactory;

    JwtCodec(KeyPair keyPair, String keyId) {
        this.keyId = keyId;
        this.signer = new JwtSigner(keyPair.getPrivate());
        this.verifier = new JwtVerifier(keyPair.getPublic());
        this.jwksKeyFactory = new JwksKeyFactory(keyPair.getPublic(), keyId);
    }

    JwtToken issue(AccessTokenClaims claims) {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + escape(keyId) + "\"}";
        String payload = claimsCodec.write(claims);
        String signingInput = JwtEncoding.base64Url(header) + "." + JwtEncoding.base64Url(payload);
        String signature = signer.sign(signingInput);
        return new JwtToken(signingInput + "." + signature, claims);
    }

    AccessTokenClaims verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IdentityException("invalid token format");
        }
        String signingInput = parts[0] + "." + parts[1];
        Map<String, String> header = JwtJson.read(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
        validateHeader(header);
        if (!verifier.verify(signingInput, parts[2])) {
            throw new IdentityException("invalid token signature");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        AccessTokenClaims claims = claimsCodec.read(payload);
        if (Instant.now().isAfter(claims.expiresAt())) {
            throw new IdentityException("access token expired");
        }
        return claims;
    }

    JsonWebKey publicJwk() {
        return jwksKeyFactory.create();
    }

    static boolean verifySignature(PublicKey publicKey, byte[] payload, byte[] signatureBytes) {
        return DeviceSignatureVerifier.verify(publicKey, payload, signatureBytes);
    }

    private void validateHeader(Map<String, String> header) {
        if (!"RS256".equals(header.get("alg"))) {
            throw new IdentityException("unsupported token algorithm");
        }
        if (!keyId.equals(header.get("kid"))) {
            throw new IdentityException("unknown token key id");
        }
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

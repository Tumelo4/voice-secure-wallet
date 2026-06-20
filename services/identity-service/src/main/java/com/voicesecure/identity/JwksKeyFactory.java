package com.voicesecure.identity;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;

final class JwksKeyFactory {
    private final PublicKey publicKey;
    private final String keyId;

    JwksKeyFactory(PublicKey publicKey, String keyId) {
        this.publicKey = publicKey;
        this.keyId = keyId;
    }

    JsonWebKey create() {
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
                JwtEncoding.base64Url(modulus.toByteArray()),
                JwtEncoding.base64Url(exponent.toByteArray())
        );
    }
}

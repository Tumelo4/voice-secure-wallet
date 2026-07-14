package com.voicesecure.identity;

import java.security.KeyPairGenerator;

public final class JwksKeyFactoryTests {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        JsonWebKey key = new JwksKeyFactory(generator.generateKeyPair().getPublic(), "rotation-key").create();
        if (!"rotation-key".equals(key.keyId()) || !"RSA".equals(key.keyType())
                || !"RS256".equals(key.algorithm()) || key.modulus().isBlank() || key.exponent().isBlank()) {
            throw new AssertionError("JWKS key must expose pinned RSA signing metadata");
        }
        System.out.println("JWKS key factory tests passed: 1");
    }
}

package com.voicesecure.identity;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;

public final class DeviceSignatureVerifierTests {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        byte[] payload = "payment:123".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keys.getPrivate());
        signer.update(payload);
        byte[] signature = signer.sign();
        if (!DeviceSignatureVerifier.verify(keys.getPublic(), payload, signature)) {
            throw new AssertionError("valid device signature must verify");
        }
        if (DeviceSignatureVerifier.verify(keys.getPublic(), "payment:124".getBytes(StandardCharsets.UTF_8), signature)) {
            throw new AssertionError("tampered device payload must be rejected");
        }
        if (JwtCodec.verifySignature(keys.getPublic(), "payment:124".getBytes(StandardCharsets.UTF_8), signature)) {
            throw new AssertionError("JWT signature facade must reject a tampered device payload");
        }
        System.out.println("Device signature verifier tests passed: 2");
    }
}

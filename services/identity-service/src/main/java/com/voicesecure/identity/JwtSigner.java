package com.voicesecure.identity;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;

final class JwtSigner {
    private final PrivateKey privateKey;

    JwtSigner(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    String sign(String signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return JwtEncoding.base64Url(signature.sign());
        } catch (Exception ex) {
            throw new IdentityException("unable to sign token: " + ex.getMessage());
        }
    }
}

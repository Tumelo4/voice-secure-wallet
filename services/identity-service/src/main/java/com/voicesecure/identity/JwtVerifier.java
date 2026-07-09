package com.voicesecure.identity;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

final class JwtVerifier {
    private final PublicKey publicKey;

    JwtVerifier(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    boolean verify(String signingInput, String encodedSignature) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getUrlDecoder().decode(encodedSignature));
        } catch (Exception ex) {
            throw new IdentityException("unable to verify token: " + ex.getMessage());
        }
    }
}

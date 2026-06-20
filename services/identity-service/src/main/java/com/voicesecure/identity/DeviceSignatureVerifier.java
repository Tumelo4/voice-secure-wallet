package com.voicesecure.identity;

import java.security.PublicKey;
import java.security.Signature;

final class DeviceSignatureVerifier {
    private DeviceSignatureVerifier() {
    }

    static boolean verify(PublicKey publicKey, byte[] payload, byte[] signatureBytes) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(payload);
            return signature.verify(signatureBytes);
        } catch (Exception ex) {
            throw new IdentityException("unable to verify device signature: " + ex.getMessage());
        }
    }
}

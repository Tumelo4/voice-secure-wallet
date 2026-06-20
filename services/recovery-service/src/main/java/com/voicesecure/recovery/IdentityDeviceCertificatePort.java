package com.voicesecure.recovery;

import com.voicesecure.identity.DeviceRegistration;
import com.voicesecure.identity.IdentityService;
import java.security.PublicKey;
import java.util.Objects;
import java.util.UUID;

public final class IdentityDeviceCertificatePort implements DeviceCertificatePort {
    private final IdentityService identityService;

    public IdentityDeviceCertificatePort(IdentityService identityService) {
        this.identityService = Objects.requireNonNull(identityService, "identityService");
    }

    @Override
    public DeviceRegistration reissueDeviceCertificate(UUID userId, UUID deviceId, PublicKey publicKey) {
        return identityService.reissueDeviceCertificate(userId, deviceId, publicKey);
    }
}

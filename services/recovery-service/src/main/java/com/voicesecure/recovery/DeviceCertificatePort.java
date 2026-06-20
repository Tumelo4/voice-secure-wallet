package com.voicesecure.recovery;

import com.voicesecure.identity.DeviceRegistration;
import java.security.PublicKey;
import java.util.UUID;

public interface DeviceCertificatePort {
    DeviceRegistration reissueDeviceCertificate(UUID userId, UUID deviceId, PublicKey publicKey);
}

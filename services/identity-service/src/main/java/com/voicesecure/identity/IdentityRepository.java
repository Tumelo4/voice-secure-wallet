package com.voicesecure.identity;

import java.util.Optional;
import java.util.UUID;

public interface IdentityRepository {
    void saveDevice(DeviceRegistration registration);

    Optional<DeviceRegistration> findDevice(UUID deviceId);

    Optional<DeviceRegistration> findDevice(UUID userId, UUID deviceId);

    void saveFamily(RefreshTokenFamilyState familyState);

    Optional<RefreshTokenFamilyState> findFamily(UUID familyId);
}


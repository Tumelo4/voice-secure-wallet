package com.voicesecure.identity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryIdentityRepository implements IdentityRepository {
    private final Map<UUID, DeviceRegistration> devicesByDeviceId = new HashMap<>();
    private final Map<UUID, RefreshTokenFamilyState> familiesById = new HashMap<>();

    @Override
    public synchronized void saveDevice(DeviceRegistration registration) {
        devicesByDeviceId.put(registration.deviceId(), registration);
    }

    @Override
    public synchronized Optional<DeviceRegistration> findDevice(UUID deviceId) {
        return Optional.ofNullable(devicesByDeviceId.get(deviceId));
    }

    @Override
    public synchronized Optional<DeviceRegistration> findDevice(UUID userId, UUID deviceId) {
        return Optional.ofNullable(devicesByDeviceId.get(deviceId))
                .filter(registration -> registration.userId().equals(userId));
    }

    @Override
    public synchronized void saveFamily(RefreshTokenFamilyState familyState) {
        familiesById.put(familyState.familyId(), familyState.copy());
    }

    @Override
    public synchronized Optional<RefreshTokenFamilyState> findFamily(UUID familyId) {
        RefreshTokenFamilyState family = familiesById.get(familyId);
        return Optional.ofNullable(family == null ? null : family.copy());
    }
}


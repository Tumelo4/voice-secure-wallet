package com.voicesecure.recovery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryRecoveryRepository implements RecoveryRepository {
    private final Map<UUID, RecoveryCase> recoveriesById = new LinkedHashMap<>();

    @Override
    public synchronized void save(RecoveryCase recoveryCase) {
        recoveriesById.put(recoveryCase.recoveryId(), recoveryCase);
    }

    @Override
    public synchronized Optional<RecoveryCase> findById(UUID recoveryId) {
        return Optional.ofNullable(recoveriesById.get(recoveryId));
    }

    @Override
    public synchronized List<RecoveryCase> all() {
        return new ArrayList<>(recoveriesById.values());
    }
}

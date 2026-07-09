package com.voicesecure.recovery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecoveryRepository {
    void save(RecoveryCase recoveryCase);

    Optional<RecoveryCase> findById(UUID recoveryId);

    List<RecoveryCase> all();
}

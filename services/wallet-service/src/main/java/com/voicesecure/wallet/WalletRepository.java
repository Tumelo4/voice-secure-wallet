package com.voicesecure.wallet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository {
    void saveAccount(WalletAccount account);

    Optional<WalletAccount> findAccount(UUID accountId);

    List<WalletAccount> accountsForUser(UUID userId);

    void saveBalance(WalletBalance balance);

    Optional<WalletBalance> findBalance(UUID accountId);

    boolean hasProcessedEvent(UUID eventId);

    void markProcessedEvent(UUID eventId);
}

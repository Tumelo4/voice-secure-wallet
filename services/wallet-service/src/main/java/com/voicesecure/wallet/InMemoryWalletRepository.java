package com.voicesecure.wallet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class InMemoryWalletRepository implements WalletRepository {
    private final Map<UUID, WalletAccount> accountsById = new HashMap<>();
    private final Map<UUID, WalletBalance> balancesByAccountId = new HashMap<>();
    private final Set<UUID> processedEventIds = new HashSet<>();

    @Override
    public synchronized void saveAccount(WalletAccount account) {
        if (accountsById.containsKey(account.accountId())) {
            throw new WalletException("wallet account already exists");
        }
        accountsById.put(account.accountId(), account);
    }

    @Override
    public synchronized Optional<WalletAccount> findAccount(UUID accountId) {
        return Optional.ofNullable(accountsById.get(accountId));
    }

    @Override
    public synchronized List<WalletAccount> accountsForUser(UUID userId) {
        List<WalletAccount> accounts = new ArrayList<>();
        for (WalletAccount account : accountsById.values()) {
            if (account.userId().equals(userId)) {
                accounts.add(account);
            }
        }
        accounts.sort(Comparator.comparing(WalletAccount::openedAt).thenComparing(WalletAccount::accountId));
        return List.copyOf(accounts);
    }

    @Override
    public synchronized void saveBalance(WalletBalance balance) {
        balancesByAccountId.put(balance.accountId(), balance);
    }

    @Override
    public synchronized Optional<WalletBalance> findBalance(UUID accountId) {
        return Optional.ofNullable(balancesByAccountId.get(accountId));
    }

    @Override
    public synchronized boolean hasProcessedEvent(UUID eventId) {
        return processedEventIds.contains(eventId);
    }

    @Override
    public synchronized void markProcessedEvent(UUID eventId) {
        processedEventIds.add(eventId);
    }
}

package com.voicesecure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.voicesecure.beneficiaries.Beneficiary;
import com.voicesecure.beneficiaries.BeneficiaryStatus;
import com.voicesecure.beneficiaries.PostgresBeneficiaryRepository;
import com.voicesecure.wallet.PostgresWalletRepository;
import com.voicesecure.wallet.WalletAccount;
import com.voicesecure.wallet.WalletBalance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
final class PostgresAccountAdaptersIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    private static PGSimpleDataSource dataSource;

    @BeforeAll static void migrate() throws Exception {
        dataSource = new PGSimpleDataSource(); dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername()); dataSource.setPassword(POSTGRES.getPassword());
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(Path.of("services/wallet-service/src/main/resources/db/migration/V001__wallet_storage.sql")));
            statement.execute(Files.readString(Path.of("services/beneficiary-service/src/main/resources/db/migration/V001__beneficiary_storage.sql")));
        }
    }

    @Test void walletAndBeneficiaryStateSurvivesRepositoryInstances() {
        UUID userId = UUID.randomUUID(); UUID accountId = UUID.randomUUID(); UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T10:00:00Z");
        PostgresWalletRepository writer = new PostgresWalletRepository(dataSource);
        writer.saveAccount(new WalletAccount(userId, accountId, "Primary", "ZAR", now));
        writer.saveBalance(new WalletBalance(accountId, "ZAR", 250_00, 1, now));
        writer.markProcessedEvent(eventId);
        PostgresWalletRepository reader = new PostgresWalletRepository(dataSource);
        assertEquals(250_00, reader.findBalance(accountId).orElseThrow().balance());
        assertEquals(1, reader.accountsForUser(userId).size());
        assertTrue(reader.hasProcessedEvent(eventId));

        UUID beneficiaryId = UUID.randomUUID(); UUID destination = UUID.randomUUID();
        PostgresBeneficiaryRepository beneficiaries = new PostgresBeneficiaryRepository(dataSource);
        beneficiaries.save(new Beneficiary(beneficiaryId, userId, destination, "Savings", "****1234", "ZAR",
                BeneficiaryStatus.ACTIVE, now, now));
        assertEquals(beneficiaryId, new PostgresBeneficiaryRepository(dataSource)
                .findByCustomerAndDestination(userId, destination).orElseThrow().beneficiaryId());
    }
}

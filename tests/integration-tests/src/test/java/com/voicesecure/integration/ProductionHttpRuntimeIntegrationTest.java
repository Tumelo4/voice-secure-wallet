package com.voicesecure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.voicesecure.api.*;
import com.voicesecure.beneficiaries.Beneficiary;
import com.voicesecure.beneficiaries.BeneficiaryStatus;
import com.voicesecure.beneficiaries.PostgresBeneficiaryRepository;
import com.voicesecure.events.KafkaClientRecordPublisher;
import com.voicesecure.events.KafkaEventPublisher;
import com.voicesecure.payments.AuthPolicy;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.wallet.PostgresWalletRepository;
import com.voicesecure.wallet.WalletAccount;
import com.voicesecure.wallet.WalletBalance;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
final class ProductionHttpRuntimeIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Container static final RedpandaContainer REDPANDA = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v25.3.4");
    private static PGSimpleDataSource dataSource;
    private static final UUID CUSTOMER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SOURCE = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID DESTINATION = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID BENEFICIARY = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @BeforeAll static void migrateAndSeed() throws Exception {
        dataSource = new PGSimpleDataSource(); dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername()); dataSource.setPassword(POSTGRES.getPassword());
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : List.of(
                    "services/payment-service/src/main/resources/db/migration/V001__payment_saga.sql",
                    "services/payment-service/src/main/resources/db/migration/V002__payment_saga_version.sql",
                    "services/payment-service/src/main/resources/db/migration/V003__payment_reconciliation_states.sql",
                    "services/payment-service/src/main/resources/db/migration/V004__customer_payment_references.sql",
                    "services/payment-service/src/main/resources/db/migration/V005__transactional_outbox.sql",
                    "services/payment-service/src/main/resources/db/migration/V006__outbox_dead_letters.sql",
                    "services/payment-service/src/main/resources/db/migration/V007__payment_recovery_audit.sql",
                    "services/ledger-service/src/main/resources/db/migration/V001__ledger_core.sql",
                    "services/ledger-service/src/main/resources/db/migration/V002__ledger_production.sql",
                    "services/ledger-service/src/main/resources/db/migration/V003__fund_reservations.sql",
                    "services/ledger-service/src/main/resources/db/migration/V004__repair_dual_control.sql",
                    "services/ledger-service/src/main/resources/db/migration/V005__batch_scoped_idempotency.sql",
                    "services/ledger-service/src/main/resources/db/migration/V006__outbox_delivery_leases.sql",
                    "services/ledger-service/src/main/resources/db/migration/V007__outbox_dead_letters.sql",
                    "services/wallet-service/src/main/resources/db/migration/V001__wallet_storage.sql",
                    "services/beneficiary-service/src/main/resources/db/migration/V001__beneficiary_storage.sql",
                    "services/support-service/src/main/resources/db/migration/V001__support_storage.sql")) {
                statement.execute(Files.readString(Path.of(file)));
            }
        }
        Instant now = Instant.parse("2026-07-16T10:00:00Z");
        PostgresWalletRepository wallets = new PostgresWalletRepository(dataSource);
        wallets.saveAccount(new WalletAccount(CUSTOMER, SOURCE, "Primary", "ZAR", now));
        wallets.saveAccount(new WalletAccount(UUID.randomUUID(), DESTINATION, "Destination", "ZAR", now));
        wallets.saveBalance(new WalletBalance(SOURCE, "ZAR", 500_00, 1, now));
        wallets.saveBalance(new WalletBalance(DESTINATION, "ZAR", 0, 1, now));
        new PostgresBeneficiaryRepository(dataSource).save(new Beneficiary(BENEFICIARY, CUSTOMER, DESTINATION,
                "Recipient", "****1234", "ZAR", BeneficiaryStatus.ACTIVE, now, now));
    }

    @Test void realHttpRuntimePublishesOutboxAndRecoversPaymentAfterRestart() throws Exception {
        String reference;
        try (ProductionApiRuntime runtime = runtime(); ApiHttpServer server = ApiHttpServer.start(runtime.apiRuntime());
             KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of("payments"));
            HttpResponse<String> started = send(server.uri("/v1/payments"), "POST", UUID.randomUUID().toString(),
                    "{\"sourceAccountId\":\"" + SOURCE + "\",\"beneficiaryId\":\"" + BENEFICIARY
                            + "\",\"amount\":{\"value\":\"75.00\",\"currency\":\"ZAR\"},\"reference\":\"integration\"}");
            assertEquals(202, started.statusCode(), started.body());
            reference = jsonString(started.body(), "paymentReference");
            boolean published = false;
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (!published && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record
                        : consumer.poll(Duration.ofMillis(500)).records("payments")) {
                    if (record.value().contains("payment.initiated")) {
                        published = true;
                        break;
                    }
                }
            }
            assertTrue(published, "payment outbox event must reach Redpanda");
        }

        try (ProductionApiRuntime restarted = runtime(); ApiHttpServer server = ApiHttpServer.start(restarted.apiRuntime())) {
            HttpResponse<String> recovered = send(server.uri("/v1/payments/" + reference), "GET", null, "");
            assertEquals(200, recovered.statusCode(), recovered.body());
            assertTrue(recovered.body().contains("AUTHORISATION_REQUIRED"));
        }
    }

    private static ProductionApiRuntime runtime() {
        KafkaClientRecordPublisher kafka = new KafkaClientRecordPublisher(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers(),
                "security.protocol", "PLAINTEXT"), Duration.ofSeconds(5));
        JedisRateLimitScriptExecutor redis = new JedisRateLimitScriptExecutor(URI.create(
                "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379)));
        ApiPrincipal principal = ApiPrincipal.of(CUSTOMER.toString(), "wallet:payment", "wallet:read", "wallet:beneficiary");
        VoiceGatewayClient voice = new VoiceGatewayClient() {
            public Challenge issueChallenge(UUID customer, String phrase, String binding) { throw new AssertionError("not used"); }
            public String verify(Verification request) { throw new AssertionError("not used"); }
        };
        return ProductionApiRuntime.assemble(new ProductionApiRuntime.Dependencies(
                dataSource, new KafkaEventPublisher(kafka), token -> Optional.of(principal),
                new RedisApiRateLimiter(redis, Clock.systemUTC(), RateLimitTelemetry.NOOP),
                request -> new FraudDecision(0.1, AuthPolicy.VOICE_OTP, true, ""), voice,
                (bank, account) -> new BeneficiaryAccountDirectory.ResolvedBeneficiaryAccount(DESTINATION, "ZAR", true),
                entry -> { }, Clock.systemUTC(), List.of(kafka, redis)));
    }

    private static KafkaConsumer<String, String> consumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, REDPANDA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "production-runtime-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }

    private static HttpResponse<String> send(URI uri, String method, String idempotencyKey, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).header("Authorization", "Bearer integration")
                .header("X-Trace-Id", "trace-integration");
        if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
        if ("POST".equals(method)) request.POST(HttpRequest.BodyPublishers.ofString(body)).header("Content-Type", "application/json");
        else request.GET();
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String jsonString(String body, String field) {
        String marker = "\"" + field + "\":\""; int start = body.indexOf(marker) + marker.length();
        return body.substring(start, body.indexOf('"', start));
    }
}

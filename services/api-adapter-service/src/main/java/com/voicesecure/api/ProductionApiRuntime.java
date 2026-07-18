package com.voicesecure.api;

import com.voicesecure.beneficiaries.BeneficiaryRiskPolicy;
import com.voicesecure.beneficiaries.BeneficiaryService;
import com.voicesecure.beneficiaries.PostgresBeneficiaryRepository;
import com.voicesecure.events.KafkaClientRecordPublisher;
import com.voicesecure.events.KafkaEventPublisher;
import com.voicesecure.events.EventPublisher;
import com.voicesecure.ledger.LedgerProductionRuntime;
import com.voicesecure.payments.PaymentProductionRuntime;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.payments.PostgresPaymentSagaRepository;
import com.voicesecure.support.PostgresSupportRepository;
import com.voicesecure.support.SupportService;
import com.voicesecure.wallet.PostgresWalletRepository;
import com.voicesecure.wallet.WalletService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.apache.kafka.clients.producer.ProducerConfig;

public final class ProductionApiRuntime implements AutoCloseable {
    private final ApiRuntime apiRuntime;
    private final List<AutoCloseable> resources;

    private ProductionApiRuntime(ApiRuntime apiRuntime, List<AutoCloseable> resources) {
        this.apiRuntime = apiRuntime;
        this.resources = List.copyOf(resources);
    }

    public static ProductionApiRuntime create(Map<String, String> environment) {
        Objects.requireNonNull(environment);
        ProductionConfiguration config = ProductionConfiguration.fromEnvironment(environment);
        validateEnvironment(environment);
        Clock clock = Clock.systemUTC();
        HikariDataSource dataSource = dataSource(config, environment);
        KafkaClientRecordPublisher kafkaClient = null;
        JedisRateLimitScriptExecutor redis = null;
        try {
            kafkaClient = new KafkaClientRecordPublisher(kafkaConfiguration(config, environment), config.remoteTimeout());
            redis = new JedisRateLimitScriptExecutor(config.redisUri());
            return assemble(new Dependencies(dataSource, new KafkaEventPublisher(kafkaClient),
                    new OidcJwksBearerTokenVerifier(config.oidcJwksUri(), config.oidcIssuer(), config.oidcAudience(), clock),
                    new RedisApiRateLimiter(redis, clock, new StructuredRateLimitTelemetry(System.out)),
                    new HttpFraudDecisionProvider(config.fraudServiceUri(), required(environment, "FRAUD_SERVICE_TOKEN"), config.remoteTimeout()),
                    new HttpVoiceGatewayClient(config.voiceServiceUri(), required(environment, "VOICE_SERVICE_TOKEN"), config.remoteTimeout()),
                    new HttpBeneficiaryAccountDirectory(config.beneficiaryDirectoryUri(),
                            required(environment, "BENEFICIARY_DIRECTORY_TOKEN"), config.remoteTimeout()),
                    new StructuredApiRequestLogSink(System.out), clock,
                    List.of(dataSource, kafkaClient, redis)));
        } catch (RuntimeException failure) {
            closeQuietly(redis); closeQuietly(kafkaClient); closeQuietly(dataSource);
            throw failure;
        }
    }

    public static ProductionApiRuntime assemble(Dependencies dependencies) {
        Objects.requireNonNull(dependencies);
        Consumer<RuntimeException> workerFailure = failure -> System.err.println(
                "{\"type\":\"background_worker_failure\",\"error\":" + ApiJson.quote(failure.getClass().getSimpleName()) + "}");
        PostgresPaymentSagaRepository paymentRepository = new PostgresPaymentSagaRepository(dependencies.dataSource());
        PaymentSagaService paymentService = new PaymentSagaService(paymentRepository);
        PaymentProductionRuntime payments = null;
        LedgerProductionRuntime ledger = null;
        try {
            ledger = new LedgerProductionRuntime(dependencies.dataSource(), dependencies.publisher(), dependencies.clock(), workerFailure);
            PaymentSettlementCoordinator settlement = new PaymentSettlementCoordinator(paymentService, ledger.ledgerService());
            payments = new PaymentProductionRuntime(dependencies.dataSource(), dependencies.publisher(), dependencies.clock(),
                    workerFailure, paymentRepository, paymentService, settlement);
            WalletService wallets = new WalletService(new PostgresWalletRepository(dependencies.dataSource()));
            BeneficiaryService beneficiaries = new BeneficiaryService(new PostgresBeneficiaryRepository(dependencies.dataSource()),
                    BeneficiaryRiskPolicy.standard(), dependencies.clock());
            PostgresPaymentReferenceRegistry references = new PostgresPaymentReferenceRegistry(dependencies.dataSource());
            SupportService support = new SupportService(new PostgresSupportRepository(dependencies.dataSource()), ledger.ledgerService());
            ApiRuntime api = new ApiRuntime(new ApiRouter(List.of(
                    new HealthApiAdapter(), new WalletApiAdapter(wallets),
                    new BeneficiaryApiAdapter(beneficiaries, dependencies.beneficiaryDirectory()),
                    new PaymentApiAdapter(payments.paymentService(), dependencies.fraud(), wallets, beneficiaries,
                            PaymentRolloutPolicy.enabled(), references,
                            settlement),
                    new SupportRepairApiAdapter(support),
                    new VoiceGatewayApiAdapter(payments.paymentService(), references, dependencies.voice()))),
                    dependencies.tokenVerifier(), dependencies.rateLimiter(), dependencies.logSink());
            List<AutoCloseable> resources = new ArrayList<>();
            resources.addAll(dependencies.infrastructureResources()); resources.add(payments); resources.add(ledger);
            return new ProductionApiRuntime(api, resources);
        } catch (RuntimeException failure) {
            closeQuietly(ledger); closeQuietly(payments); throw failure;
        }
    }

    public record Dependencies(DataSource dataSource, EventPublisher publisher, BearerTokenVerifier tokenVerifier,
                               ApiRateLimiter rateLimiter, FraudDecisionProvider fraud, VoiceGatewayClient voice,
                               BeneficiaryAccountDirectory beneficiaryDirectory, ApiRequestLogSink logSink,
                               Clock clock, List<AutoCloseable> infrastructureResources) {
        public Dependencies {
            Objects.requireNonNull(dataSource); Objects.requireNonNull(publisher); Objects.requireNonNull(tokenVerifier);
            Objects.requireNonNull(rateLimiter); Objects.requireNonNull(fraud); Objects.requireNonNull(voice);
            Objects.requireNonNull(beneficiaryDirectory); Objects.requireNonNull(logSink); Objects.requireNonNull(clock);
            infrastructureResources = List.copyOf(Objects.requireNonNull(infrastructureResources));
        }
    }

    public ApiRuntime apiRuntime() { return apiRuntime; }

    static void validateEnvironment(Map<String, String> environment) {
        required(environment, "FRAUD_SERVICE_TOKEN");
        required(environment, "VOICE_SERVICE_TOKEN");
        required(environment, "BENEFICIARY_DIRECTORY_TOKEN");
        int poolSize;
        try { poolSize = Integer.parseInt(environment.getOrDefault("DATABASE_POOL_SIZE", "20")); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("DATABASE_POOL_SIZE must be an integer", invalid); }
        if (poolSize < 2 || poolSize > 100) throw new IllegalArgumentException("DATABASE_POOL_SIZE must be between 2 and 100");
        String protocol = environment.getOrDefault("KAFKA_SECURITY_PROTOCOL", "SASL_SSL");
        boolean localPlaintext = "PLAINTEXT".equals(protocol)
                && Boolean.parseBoolean(environment.getOrDefault("ALLOW_INSECURE_LOCAL_DEPENDENCIES", "false"));
        if ("PLAINTEXT".equals(protocol) && !localPlaintext) {
            throw new IllegalArgumentException("KAFKA_SECURITY_PROTOCOL=PLAINTEXT is allowed only for explicit local integration tests");
        }
        if (protocol.startsWith("SASL")) {
            required(environment, "KAFKA_SASL_MECHANISM");
            if (!environment.containsKey("KAFKA_SASL_JAAS_CONFIG") && !environment.containsKey("KAFKA_SASL_CALLBACK_HANDLER_CLASS")) {
                throw new IllegalArgumentException("SASL Kafka requires JAAS or callback-handler configuration");
            }
        }
    }

    @Override
    public void close() {
        for (int index = resources.size() - 1; index >= 0; index--) closeQuietly(resources.get(index));
    }

    private static HikariDataSource dataSource(ProductionConfiguration config, Map<String, String> environment) {
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(config.databaseUrl()); pool.setUsername(config.databaseUser()); pool.setPassword(config.databasePassword());
        pool.setMaximumPoolSize(Integer.parseInt(environment.getOrDefault("DATABASE_POOL_SIZE", "20")));
        pool.setMinimumIdle(2); pool.setConnectionTimeout(config.remoteTimeout().toMillis());
        pool.setPoolName("voicesecure-api"); pool.setAutoCommit(true);
        return new HikariDataSource(pool);
    }

    private static Map<String, Object> kafkaConfiguration(ProductionConfiguration config, Map<String, String> environment) {
        String protocol = environment.getOrDefault("KAFKA_SECURITY_PROTOCOL", "SASL_SSL");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.kafkaBootstrapServers());
        values.put("security.protocol", protocol);
        copy(environment, values, "KAFKA_SASL_MECHANISM", "sasl.mechanism");
        copy(environment, values, "KAFKA_SASL_JAAS_CONFIG", "sasl.jaas.config");
        copy(environment, values, "KAFKA_SASL_CALLBACK_HANDLER_CLASS", "sasl.client.callback.handler.class");
        values.put(ProducerConfig.CLIENT_ID_CONFIG, environment.getOrDefault("KAFKA_CLIENT_ID", "voicesecure-api"));
        values.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, Long.toString(config.remoteTimeout().toMillis()));
        return values;
    }

    private static void copy(Map<String, String> source, Map<String, Object> target, String sourceName, String targetName) {
        String value = source.get(sourceName); if (value != null && !value.isBlank()) target.put(targetName, value.trim());
    }
    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name); if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); return value.trim();
    }
    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) try { closeable.close(); } catch (Exception ignored) { }
    }
}

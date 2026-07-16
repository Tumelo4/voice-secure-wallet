package com.voicesecure.api;

import com.voicesecure.beneficiaries.BeneficiaryRiskPolicy;
import com.voicesecure.beneficiaries.BeneficiaryService;
import com.voicesecure.beneficiaries.InMemoryBeneficiaryRepository;
import com.voicesecure.identity.IdentityService;
import com.voicesecure.identity.InMemoryIdentityRepository;
import com.voicesecure.ledger.application.LedgerService;
import com.voicesecure.ledger.infrastructure.InMemoryLedgerRepository;
import com.voicesecure.payments.AuthPolicy;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.InMemoryPaymentSagaRepository;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.support.InMemorySupportRepository;
import com.voicesecure.support.SupportService;
import com.voicesecure.wallet.InMemoryWalletRepository;
import com.voicesecure.wallet.WalletService;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public final class ApiApplication {
    private ApiApplication() { }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        ProductionApiRuntime production = null;
        ApiRuntime runtime;
        if ("production".equalsIgnoreCase(System.getenv("VSW_ENVIRONMENT"))) {
            production = ProductionApiRuntime.create(System.getenv());
            runtime = production.apiRuntime();
        } else {
            runtime = createRuntime();
        }
        ApiHttpServer server = ApiHttpServer.start(new InetSocketAddress("0.0.0.0", port), runtime);
        ProductionApiRuntime managed = production;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            if (managed != null) managed.close();
        }, "api-shutdown"));
        new CountDownLatch(1).await();
    }

    static ApiRuntime createRuntime() throws Exception {
        KeyPairGenerator keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        IdentityService identity = new IdentityService(new InMemoryIdentityRepository(), keys.generateKeyPair(), "local-key");
        WalletService wallets = new WalletService(new InMemoryWalletRepository());
        BeneficiaryService beneficiaries = new BeneficiaryService(
                new InMemoryBeneficiaryRepository(), BeneficiaryRiskPolicy.standard(), Clock.systemUTC());
        PaymentSagaService payments = new PaymentSagaService(new InMemoryPaymentSagaRepository());
        InMemoryPaymentReferenceRegistry paymentReferences = new InMemoryPaymentReferenceRegistry();
        SupportService support = new SupportService(
                new InMemorySupportRepository(), new LedgerService(new InMemoryLedgerRepository()));
        List<ApiEndpoint> endpoints = new ArrayList<>(List.of(
                new HealthApiAdapter(),
                new WalletApiAdapter(wallets),
                new BeneficiaryApiAdapter(beneficiaries, (bank, account) ->
                        new BeneficiaryAccountDirectory.ResolvedBeneficiaryAccount(
                                UUID.nameUUIDFromBytes((bank + ":" + account).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "ZAR", true)),
                new PaymentApiAdapter(payments,
                        request -> new FraudDecision(0.1, AuthPolicy.VOICE_OTP, true, ""), wallets, beneficiaries,
                        PaymentRolloutPolicy.enabled(), paymentReferences),
                new SupportRepairApiAdapter(support)
        ));
        String voiceServiceUri = System.getenv("VOICE_SERVICE_URI");
        if (voiceServiceUri != null && !voiceServiceUri.isBlank()) {
            endpoints.add(new VoiceGatewayApiAdapter(
                    payments, paymentReferences,
                    new HttpVoiceGatewayClient(URI.create(voiceServiceUri),
                            requiredEnvironment("VOICE_SERVICE_TOKEN"), Duration.ofMillis(2000))));
        }
        ApiRouter router = new ApiRouter(endpoints);
        return new ApiRuntime(
                router, new IdentityBearerTokenVerifier(identity),
                createRateLimiter(),
                new InMemoryApiRequestLogSink());
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value.trim();
    }

    private static ApiRateLimiter createRateLimiter() {
        String redisUri = System.getenv("REDIS_URI");
        if (redisUri == null || redisUri.isBlank()) {
            return new InMemoryApiRateLimiter(Integer.parseInt(System.getenv().getOrDefault("RATE_LIMIT", "1000")));
        }
        URI uri = URI.create(redisUri);
        if ("production".equalsIgnoreCase(System.getenv("VSW_ENVIRONMENT")) && !"rediss".equals(uri.getScheme())) {
            throw new IllegalStateException("production Redis rate limiting requires a rediss:// URI");
        }
        return new RedisApiRateLimiter(
                new JedisRateLimitScriptExecutor(uri), Clock.systemUTC(), RateLimitTelemetry.NOOP);
    }
}

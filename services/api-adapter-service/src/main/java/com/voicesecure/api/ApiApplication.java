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
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public final class ApiApplication {
    private ApiApplication() { }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        ApiRuntime runtime = createRuntime();
        ApiHttpServer server = ApiHttpServer.start(new InetSocketAddress("0.0.0.0", port), runtime);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "api-shutdown"));
        new CountDownLatch(1).await();
    }

    static ApiRuntime createRuntime() throws Exception {
        if ("production".equalsIgnoreCase(System.getenv("VSW_ENVIRONMENT"))) {
            throw new IllegalStateException("production bootstrap requires managed persistence and signing-key adapters");
        }
        KeyPairGenerator keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(2048);
        IdentityService identity = new IdentityService(new InMemoryIdentityRepository(), keys.generateKeyPair(), "local-key");
        WalletService wallets = new WalletService(new InMemoryWalletRepository());
        BeneficiaryService beneficiaries = new BeneficiaryService(
                new InMemoryBeneficiaryRepository(), BeneficiaryRiskPolicy.standard(), Clock.systemUTC());
        PaymentSagaService payments = new PaymentSagaService(new InMemoryPaymentSagaRepository());
        SupportService support = new SupportService(
                new InMemorySupportRepository(), new LedgerService(new InMemoryLedgerRepository()));
        ApiRouter router = new ApiRouter(List.of(
                new HealthApiAdapter(),
                new WalletApiAdapter(wallets),
                new BeneficiaryApiAdapter(beneficiaries, (bank, account) ->
                        new BeneficiaryAccountDirectory.ResolvedBeneficiaryAccount(
                                UUID.nameUUIDFromBytes((bank + ":" + account).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "ZAR", true)),
                new PaymentApiAdapter(payments,
                        request -> new FraudDecision(0.1, AuthPolicy.VOICE_OTP, true, ""), wallets, beneficiaries),
                new SupportRepairApiAdapter(support)
        ));
        return new ApiRuntime(
                router, new IdentityBearerTokenVerifier(identity),
                new InMemoryApiRateLimiter(Integer.parseInt(System.getenv().getOrDefault("RATE_LIMIT", "1000"))),
                new InMemoryApiRequestLogSink());
    }
}

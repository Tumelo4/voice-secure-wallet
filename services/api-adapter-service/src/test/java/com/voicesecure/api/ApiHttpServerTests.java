package com.voicesecure.api;

import com.voicesecure.beneficiaries.BeneficiaryService;
import com.voicesecure.beneficiaries.InMemoryBeneficiaryRepository;
import com.voicesecure.payments.AuthPolicy;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.InMemoryPaymentSagaRepository;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.identity.IdentityService;
import com.voicesecure.identity.InMemoryIdentityRepository;
import com.voicesecure.ledger.application.LedgerService;
import com.voicesecure.ledger.infrastructure.InMemoryLedgerRepository;
import com.voicesecure.support.InMemorySupportRepository;
import com.voicesecure.support.SupportService;
import com.voicesecure.wallet.InMemoryWalletRepository;
import com.voicesecure.wallet.WalletService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ApiHttpServerTests {
    public static void main(String[] args) throws Exception {
        TestCase[] tests = {
                new TestCase("local listener forwards wallet GET through runtime guards", ApiHttpServerTests::forwardsWalletGet),
                new TestCase("local listener forwards payment POST JSON", ApiHttpServerTests::forwardsPaymentPost),
                new TestCase("local listener forwards support repair POST JSON", ApiHttpServerTests::forwardsSupportRepairPost),
                new TestCase("local listener forwards public health GET", ApiHttpServerTests::forwardsHealthGet),
                new TestCase("local listener preserves runtime rate-limit retry headers", ApiHttpServerTests::preservesRateLimitHeaders),
                new TestCase("Jetty handles concurrent keep-alive requests", ApiHttpServerTests::handlesConcurrentKeepAliveRequests)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("API HTTP server tests passed: " + tests.length);
    }

    private static void forwardsWalletGet() throws Exception {
        Fixture fixture = fixture(10);
        try (ApiHttpServer server = ApiHttpServer.start(fixture.runtime)) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(server.uri("/wallets/" + fixture.accountId + "/balance"))
                    .header("Authorization", "Bearer " + fixture.tokenUser1)
                    .header("X-Trace-Id", "trace-http-1")
                    .GET()
                    .build());

            assertEquals(200, response.statusCode(), "wallet status");
            assertContains(response.body(), "\"balance\":1250", "wallet body");
            assertContains(response.headers().firstValue("Content-Type").orElse(""), "application/json", "content type");
            assertEquals("trace-http-1", fixture.logSink.entries().get(0).traceId(), "logged trace");
        }
    }

    private static void forwardsPaymentPost() throws Exception {
        Fixture fixture = fixture(10);
        try (ApiHttpServer server = ApiHttpServer.start(fixture.runtime)) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(server.uri("/v1/payments"))
                    .header("Authorization", "Bearer " + fixture.tokenUser1)
                    .header("X-Trace-Id", "trace-http-2")
                    .header("Idempotency-Key", "dddddddd-dddd-4ddd-8ddd-dddddddddddd")
                    .POST(HttpRequest.BodyPublishers.ofString(paymentBody(750)))
                    .build());

            assertEquals(202, response.statusCode(), "payment status");
            assertContains(response.body(), "\"state\":\"AUTHORISATION_REQUIRED\"", "payment state");
            assertEquals("/v1/payments", fixture.logSink.entries().get(0).path(), "logged path");
        }
    }

    private static void forwardsSupportRepairPost() throws Exception {
        Fixture fixture = fixture(10);
        UUID repairId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID sagaId = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        UUID idempotencyKey = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
        try (ApiHttpServer server = ApiHttpServer.start(fixture.runtime)) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(server.uri("/support/repairs"))
                    .header("Authorization", "Bearer " + fixture.tokenUser1)
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .header("X-Trace-Id", "trace-http-2b")
                    .POST(HttpRequest.BodyPublishers.ofString(repairBody(
                            repairId,
                            sagaId,
                            fixture.supportSourceAccountId,
                            fixture.supportDestinationAccountId,
                            50,
                            "COMPENSATION_FAILED drill corrective entry",
                            "sre@example.com"
                    )))
                    .build());

            assertEquals(202, response.statusCode(), "support repair status");
            assertContains(response.body(), "\"status\":\"PENDING_APPROVAL\"", "support repair status");
            assertEquals("/support/repairs", fixture.logSink.entries().get(0).path(), "logged path");
            assertEquals(1, fixture.supportRepository.cases().size(), "pending support repair case count");
            assertEquals(0, fixture.supportLedgerRepository.entries().size(), "support repair entries before approval");
        }
    }

    private static void forwardsHealthGet() throws Exception {
        Fixture fixture = fixture(10);
        try (ApiHttpServer server = ApiHttpServer.start(fixture.runtime)) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(server.uri("/health/ready"))
                    .header("X-Trace-Id", "trace-http-health-1")
                    .GET()
                    .build());

            assertEquals(200, response.statusCode(), "health status");
            assertContains(response.body(), "\"status\":\"READY\"", "health body");
            assertEquals("anonymous", fixture.logSink.entries().get(0).principalId(), "health principal");
            assertEquals("/health/ready", fixture.logSink.entries().get(0).path(), "health path");
        }
    }

    private static void preservesRateLimitHeaders() throws Exception {
        Fixture fixture = fixture(1);
        try (ApiHttpServer server = ApiHttpServer.start(fixture.runtime)) {
            HttpRequest first = HttpRequest.newBuilder(server.uri("/wallets/" + fixture.accountId + "/balance"))
                    .header("Authorization", "Bearer " + fixture.tokenUser1)
                    .header("X-Trace-Id", "trace-http-3")
                    .GET()
                    .build();
            send(first);

            HttpResponse<String> limited = send(HttpRequest.newBuilder(server.uri("/wallets/" + fixture.accountId + "/balance"))
                    .header("Authorization", "Bearer " + fixture.tokenUser1)
                    .header("X-Trace-Id", "trace-http-4")
                    .GET()
                    .build());

            assertEquals(429, limited.statusCode(), "rate-limit status");
            assertContains(limited.body(), "\"code\":\"RATE_LIMITED\"", "rate-limit body");
            assertEquals("2", limited.headers().firstValue("Retry-After").orElse(""), "retry header");
        }
    }

    private static void handlesConcurrentKeepAliveRequests() throws Exception {
        Fixture fixture = fixture(100);
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        try (ApiHttpServer server = ApiHttpServer.start(fixture.runtime)) {
            List<CompletableFuture<HttpResponse<String>>> requests = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                HttpRequest request = HttpRequest.newBuilder(server.uri("/wallets/" + fixture.accountId + "/balance"))
                        .header("Authorization", "Bearer " + fixture.tokenUser1)
                        .header("X-Trace-Id", "trace-concurrent-" + index)
                        .GET().build();
                requests.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
            }
            for (CompletableFuture<HttpResponse<String>> request : requests) {
                HttpResponse<String> response = request.join();
                assertEquals(200, response.statusCode(), "concurrent response status");
                assertEquals(HttpClient.Version.HTTP_1_1, response.version(), "HTTP keep-alive protocol");
            }
        }
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Fixture fixture(int rateLimit) {
        PaymentSagaService paymentService = new PaymentSagaService(new InMemoryPaymentSagaRepository());
        WalletService walletService = new WalletService(new InMemoryWalletRepository());
        InMemoryLedgerRepository supportLedgerRepository = new InMemoryLedgerRepository();
        LedgerService supportLedgerService = new LedgerService(supportLedgerRepository);
        InMemorySupportRepository supportRepository = new InMemorySupportRepository();
        IdentityService identityService = new IdentityService(new InMemoryIdentityRepository(), signingKeyPair(), "voice-secure-key-1");
        UUID user1Id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID user1DeviceId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        identityService.registerDevice(user1Id, user1DeviceId, generateKeyPair().getPublic());
        String tokenUser1 = identityService.createSession(
                user1Id,
                user1DeviceId,
                "wallet:payment wallet:balance support:repair",
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        ).accessToken().token();
        UUID supportSourceAccountId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        UUID supportDestinationAccountId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        supportLedgerService.createAccount(supportSourceAccountId, "ZAR", 1_000);
        supportLedgerService.createAccount(supportDestinationAccountId, "ZAR", 0);
        SupportService supportService = new SupportService(supportRepository, supportLedgerService);
        UUID accountId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        walletService.openWallet(user1Id, accountId, "Everyday wallet", "ZAR");
        UUID destinationId = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");
        walletService.openWallet(UUID.randomUUID(), destinationId, "Maya Nkosi", "ZAR");
        BeneficiaryService beneficiaryService = new BeneficiaryService(
                new InMemoryBeneficiaryRepository(), (customer, destination) -> Duration.ZERO, Clock.systemUTC());
        beneficiaryService.registerVerified(
                UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"), user1Id, destinationId,
                "Maya Nkosi", "0000EEEE", "ZAR");
        walletService.applyBalanceSnapshot(accountId, "ZAR", 1_250, Instant.parse("2026-06-20T12:00:00Z"));
        ApiRouter router = new ApiRouter(java.util.List.of(
                new HealthApiAdapter(),
                new PaymentApiAdapter(paymentService, request -> new FraudDecision(0.18, AuthPolicy.VOICE_OTP, true, ""), walletService, beneficiaryService),
                new WalletApiAdapter(walletService),
                new SupportRepairApiAdapter(supportService)
        ));
        InMemoryApiRequestLogSink logSink = new InMemoryApiRequestLogSink();
        ApiRuntime runtime = new ApiRuntime(
                router,
                new IdentityBearerTokenVerifier(identityService),
                new InMemoryApiRateLimiter(rateLimit),
                logSink
        );
        return new Fixture(runtime, logSink, accountId, supportRepository, supportLedgerRepository, supportSourceAccountId, supportDestinationAccountId, tokenUser1);
    }

    private static String paymentBody(long amount) {
        return "{"
                + "\"sourceAccountId\":\"11111111-1111-4111-8111-111111111111\","
                + "\"beneficiaryId\":\"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee\","
                + "\"amount\":{\"value\":\"" + amount + ".00\",\"currency\":\"ZAR\"},"
                + "\"reference\":\"Dinner split\""
                + "}";
    }

    private static String repairBody(
            UUID repairId,
            UUID sagaId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            long amount,
            String justification,
            String requestedBy
    ) {
        return "{"
                + "\"repairId\":\"" + repairId + "\","
                + "\"sagaId\":\"" + sagaId + "\","
                + "\"sourceAccountId\":\"" + sourceAccountId + "\","
                + "\"destinationAccountId\":\"" + destinationAccountId + "\","
                + "\"amount\":" + amount + ","
                + "\"currency\":\"ZAR\","
                + "\"justification\":\"" + justification + "\","
                + "\"requestedBy\":\"" + requestedBy + "\""
                + "}";
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("unable to generate RSA key pair", ex);
        }
    }

    private static KeyPair signingKeyPair() {
        return generateKeyPair();
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            throw new AssertionError(message + ": expected to find " + expected + " in " + actual);
        }
    }

    private record Fixture(
            ApiRuntime runtime,
            InMemoryApiRequestLogSink logSink,
            UUID accountId,
            InMemorySupportRepository supportRepository,
            InMemoryLedgerRepository supportLedgerRepository,
            UUID supportSourceAccountId,
            UUID supportDestinationAccountId,
            String tokenUser1
    ) {
    }

    private record TestCase(String name, ThrowingRunnable runnable) {
        void run() throws Exception {
            runnable.run();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

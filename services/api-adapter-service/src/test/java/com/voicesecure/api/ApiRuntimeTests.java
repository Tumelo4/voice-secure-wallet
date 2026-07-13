package com.voicesecure.api;

import com.voicesecure.beneficiaries.BeneficiaryService;
import com.voicesecure.beneficiaries.InMemoryBeneficiaryRepository;
import com.voicesecure.payments.AuthPolicy;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.InMemoryPaymentSagaRepository;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.identity.IdentityService;
import com.voicesecure.identity.InMemoryIdentityRepository;
import com.voicesecure.ledger.InMemoryLedgerRepository;
import com.voicesecure.ledger.LedgerService;
import com.voicesecure.support.InMemorySupportRepository;
import com.voicesecure.support.SupportService;
import com.voicesecure.wallet.InMemoryWalletRepository;
import com.voicesecure.wallet.WalletService;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

public final class ApiRuntimeTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("runtime rejects protected routes without a bearer token", ApiRuntimeTests::rejectsMissingToken),
                new TestCase("runtime rejects invalid bearer tokens", ApiRuntimeTests::rejectsInvalidToken),
                new TestCase("runtime requires a trace id before routing", ApiRuntimeTests::requiresTraceBeforeRouting),
                new TestCase("runtime allows public health routes without a bearer token", ApiRuntimeTests::allowsPublicHealthRoute),
                new TestCase("runtime rejects requests missing route scopes", ApiRuntimeTests::rejectsMissingScope),
                new TestCase("runtime rejects support repair requests missing repair scope", ApiRuntimeTests::rejectsMissingSupportRepairScope),
                new TestCase("runtime rate limits by authenticated principal", ApiRuntimeTests::rateLimitsByPrincipal),
                new TestCase("runtime forwards valid requests and records audit log", ApiRuntimeTests::forwardsAndLogsValidRequests)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("API runtime tests passed: " + tests.length);
    }

    private static void rejectsMissingToken() {
        Fixture fixture = fixture();

        ApiResponse response = fixture.runtime.handle(new ApiRequest(
                "POST",
                "/v1/payments",
                Map.of("X-Trace-Id", "trace-runtime-1"),
                paymentBody(100)
        ));

        assertEquals(401, response.status(), "missing token status");
        assertContains(response.body(), "\"code\":\"AUTHENTICATION_REQUIRED\"", "auth error code");
        assertEquals(1, fixture.logSink.entries().size(), "failed auth should be logged");
    }

    private static void rejectsInvalidToken() {
        Fixture fixture = fixture();

        ApiResponse response = fixture.runtime.handle(new ApiRequest(
                "GET",
                "/wallets/" + fixture.accountId + "/balance",
                Map.of("X-Trace-Id", "trace-runtime-2", "Authorization", "Bearer wrong-token"),
                ""
        ));

        assertEquals(403, response.status(), "invalid token status");
        assertContains(response.body(), "\"code\":\"FORBIDDEN\"", "forbidden error code");
    }

    private static void requiresTraceBeforeRouting() {
        Fixture fixture = fixture();

        ApiResponse response = fixture.runtime.handle(new ApiRequest(
                "GET",
                "/wallets/" + fixture.accountId + "/balance",
                java.util.Map.of("Authorization", "Bearer " + fixture.tokenUser1),
                ""
        ));

        assertEquals(400, response.status(), "missing trace status");
        assertContains(response.body(), "\"code\":\"TRACE_REQUIRED\"", "trace error code");
        assertEquals(0, fixture.router.invocationCount(), "missing trace should not hit router");
    }

    private static void allowsPublicHealthRoute() {
        Fixture fixture = fixture();

        ApiResponse response = fixture.runtime.handle(new ApiRequest(
                "GET",
                "/health/live",
                Map.of("X-Trace-Id", "trace-runtime-health-1"),
                ""
        ));

        assertEquals(200, response.status(), "health status");
        assertContains(response.body(), "\"status\":\"LIVE\"", "health body");
        assertEquals(1, fixture.router.invocationCount(), "health request should hit router");
        ApiRequestLogEntry entry = fixture.logSink.entries().get(0);
        assertEquals("anonymous", entry.principalId(), "health principal");
        assertEquals("/health/live", entry.path(), "health path");
    }

    private static void rejectsMissingScope() {
        Fixture fixture = fixture();

        ApiResponse response = fixture.runtime.handle(new ApiRequest(
                "GET",
                "/wallets/" + fixture.accountId + "/balance",
                java.util.Map.of("Authorization", "Bearer " + fixture.tokenUser2, "X-Trace-Id", "trace-runtime-2b"),
                ""
        ));

        assertEquals(403, response.status(), "missing scope status");
        assertContains(response.body(), "\"code\":\"INSUFFICIENT_SCOPE\"", "scope error code");
        assertEquals(0, fixture.router.invocationCount(), "scope failure should not hit router");
        assertEquals(1, fixture.logSink.entries().size(), "scope failure should be logged");
    }

    private static void rejectsMissingSupportRepairScope() {
        Fixture fixture = fixture();

        ApiResponse response = fixture.runtime.handle(new ApiRequest(
                "POST",
                "/support/repairs",
                java.util.Map.of(
                        "Authorization", "Bearer " + fixture.tokenUser2,
                        "X-Trace-Id", "trace-runtime-2c",
                        "Idempotency-Key", UUID.randomUUID().toString()
                ),
                ""
        ));

        assertEquals(403, response.status(), "missing support repair scope status");
        assertContains(response.body(), "\"code\":\"INSUFFICIENT_SCOPE\"", "support repair scope error code");
        assertEquals(0, fixture.router.invocationCount(), "support scope failure should not hit router");
    }

    private static void rateLimitsByPrincipal() {
        Fixture fixture = fixture();
        java.util.Map<String, String> headers = java.util.Map.of("Authorization", "Bearer " + fixture.tokenUser1, "X-Trace-Id", "trace-runtime-3");

        fixture.runtime.handle(new ApiRequest("GET", "/wallets/" + fixture.accountId + "/balance", headers, ""));
        fixture.runtime.handle(new ApiRequest("GET", "/wallets/" + fixture.accountId + "/balance", headers, ""));
        ApiResponse limited = fixture.runtime.handle(new ApiRequest("GET", "/wallets/" + fixture.accountId + "/balance", headers, ""));

        assertEquals(429, limited.status(), "rate limit status");
        assertContains(limited.body(), "\"code\":\"RATE_LIMITED\"", "rate limit code");
        assertEquals("2", limited.headers().get("Retry-After"), "retry hint");
    }

    private static void forwardsAndLogsValidRequests() {
        Fixture fixture = fixture();
        ApiResponse response = fixture.runtime.handle(new ApiRequest(
                "POST",
                "/v1/payments",
                java.util.Map.of(
                        "Authorization", "Bearer " + fixture.tokenUser1,
                        "X-Trace-Id", "trace-runtime-4",
                        "Idempotency-Key", "55555555-5555-4555-8555-555555555555"
                ),
                paymentBody(750)
        ));

        assertEquals(202, response.status(), "valid payment status");
        assertContains(response.body(), "\"state\":\"AUTHORISATION_REQUIRED\"", "payment state");
        ApiRequestLogEntry entry = fixture.logSink.entries().get(0);
        assertEquals("trace-runtime-4", entry.traceId(), "logged trace");
        assertEquals(fixture.user1Id.toString(), entry.principalId(), "logged principal");
        assertEquals("/v1/payments", entry.path(), "logged path");
        assertEquals(202, entry.status(), "logged status");
    }

    private static Fixture fixture() {
        PaymentSagaService paymentService = new PaymentSagaService(new InMemoryPaymentSagaRepository());
        WalletService walletService = new WalletService(new InMemoryWalletRepository());
        SupportService supportService = new SupportService(
                new InMemorySupportRepository(),
                new LedgerService(new InMemoryLedgerRepository())
        );
        IdentityService identityService = new IdentityService(new InMemoryIdentityRepository(), signingKeyPair(), "voice-secure-key-1");
        UUID user1Id = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID user1DeviceId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        UUID user2Id = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID user2DeviceId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        identityService.registerDevice(user1Id, user1DeviceId, generateKeyPair().getPublic());
        identityService.registerDevice(user2Id, user2DeviceId, generateKeyPair().getPublic());
        UUID accountId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        walletService.openWallet(user1Id, accountId, "Everyday wallet", "ZAR");
        UUID destinationId = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");
        walletService.openWallet(user2Id, destinationId, "Maya Nkosi", "ZAR");
        BeneficiaryService beneficiaryService = new BeneficiaryService(
                new InMemoryBeneficiaryRepository(), (customer, destination) -> Duration.ZERO, Clock.systemUTC());
        beneficiaryService.registerVerified(
                UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"), user1Id, destinationId,
                "Maya Nkosi", "0000EEEE", "ZAR");
        walletService.applyBalanceSnapshot(accountId, "ZAR", 1_250, Instant.parse("2026-06-20T12:00:00Z"));
        CountingApiEndpoint router = new CountingApiEndpoint(new ApiRouter(java.util.List.of(
                new HealthApiAdapter(),
                new PaymentApiAdapter(paymentService, request -> new FraudDecision(0.18, AuthPolicy.VOICE_OTP, true, ""), walletService, beneficiaryService),
                new WalletApiAdapter(walletService),
                new SupportRepairApiAdapter(supportService)
        )));
        InMemoryApiRequestLogSink logSink = new InMemoryApiRequestLogSink();
        ApiRuntime runtime = new ApiRuntime(
                router,
                new IdentityBearerTokenVerifier(identityService),
                new InMemoryApiRateLimiter(2),
                logSink
        );
        String tokenUser1 = identityService.createSession(
                user1Id,
                user1DeviceId,
                "wallet:payment wallet:balance support:repair",
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        ).accessToken().token();
        String tokenUser2 = identityService.createSession(
                user2Id,
                user2DeviceId,
                "wallet:payment",
                Duration.ofMinutes(15),
                Duration.ofDays(7)
        ).accessToken().token();
        return new Fixture(runtime, router, logSink, accountId, user1Id, tokenUser1, tokenUser2);
    }

    private static String paymentBody(long amount) {
        return "{"
                + "\"sourceAccountId\":\"11111111-1111-4111-8111-111111111111\","
                + "\"beneficiaryId\":\"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee\","
                + "\"amount\":{\"value\":\"" + amount + ".00\",\"currency\":\"ZAR\"},"
                + "\"reference\":\"Dinner split\""
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
            CountingApiEndpoint router,
            InMemoryApiRequestLogSink logSink,
            UUID accountId,
            UUID user1Id,
            String tokenUser1,
            String tokenUser2
    ) {
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}

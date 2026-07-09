package com.voicesecure.api;

import com.voicesecure.payments.AuthPolicy;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.InMemoryPaymentSagaRepository;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.wallet.InMemoryWalletRepository;
import com.voicesecure.wallet.WalletService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class ApiRuntimeTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("runtime rejects protected routes without a bearer token", ApiRuntimeTests::rejectsMissingToken),
                new TestCase("runtime rejects invalid bearer tokens", ApiRuntimeTests::rejectsInvalidToken),
                new TestCase("runtime requires a trace id before routing", ApiRuntimeTests::requiresTraceBeforeRouting),
                new TestCase("runtime rejects requests missing route scopes", ApiRuntimeTests::rejectsMissingScope),
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
                "/payments",
                Map.of("X-Trace-Id", "trace-runtime-1", "Idempotency-Key", UUID.randomUUID().toString()),
                paymentBody(UUID.randomUUID(), 100)
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
                Map.of("Authorization", "Bearer token-user-1"),
                ""
        ));

        assertEquals(400, response.status(), "missing trace status");
        assertContains(response.body(), "\"code\":\"TRACE_REQUIRED\"", "trace error code");
        assertEquals(0, fixture.router.invocationCount(), "missing trace should not hit router");
    }

    private static void rejectsMissingScope() {
        Fixture fixture = fixture();

        ApiResponse response = fixture.runtime.handle(new ApiRequest(
                "GET",
                "/wallets/" + fixture.accountId + "/balance",
                Map.of("Authorization", "Bearer token-user-2", "X-Trace-Id", "trace-runtime-2b"),
                ""
        ));

        assertEquals(403, response.status(), "missing scope status");
        assertContains(response.body(), "\"code\":\"INSUFFICIENT_SCOPE\"", "scope error code");
        assertEquals(0, fixture.router.invocationCount(), "scope failure should not hit router");
        assertEquals(1, fixture.logSink.entries().size(), "scope failure should be logged");
    }

    private static void rateLimitsByPrincipal() {
        Fixture fixture = fixture();
        Map<String, String> headers = Map.of("Authorization", "Bearer token-user-1", "X-Trace-Id", "trace-runtime-3");

        fixture.runtime.handle(new ApiRequest("GET", "/wallets/" + fixture.accountId + "/balance", headers, ""));
        fixture.runtime.handle(new ApiRequest("GET", "/wallets/" + fixture.accountId + "/balance", headers, ""));
        ApiResponse limited = fixture.runtime.handle(new ApiRequest("GET", "/wallets/" + fixture.accountId + "/balance", headers, ""));

        assertEquals(429, limited.status(), "rate limit status");
        assertContains(limited.body(), "\"code\":\"RATE_LIMITED\"", "rate limit code");
        assertEquals("2", limited.headers().get("Retry-After"), "retry hint");
    }

    private static void forwardsAndLogsValidRequests() {
        Fixture fixture = fixture();
        UUID sagaId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID idempotencyKey = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

        ApiResponse response = fixture.runtime.handle(new ApiRequest(
                "POST",
                "/payments",
                Map.of(
                        "Authorization", "Bearer token-user-1",
                        "Idempotency-Key", idempotencyKey.toString(),
                        "X-Trace-Id", "trace-runtime-4"
                ),
                paymentBody(sagaId, 750)
        ));

        assertEquals(202, response.status(), "valid payment status");
        assertContains(response.body(), "\"state\":\"VOICE_VERIFICATION_PENDING\"", "payment state");
        ApiRequestLogEntry entry = fixture.logSink.entries().get(0);
        assertEquals("trace-runtime-4", entry.traceId(), "logged trace");
        assertEquals("user-1", entry.principalId(), "logged principal");
        assertEquals("/payments", entry.path(), "logged path");
        assertEquals(202, entry.status(), "logged status");
    }

    private static Fixture fixture() {
        PaymentSagaService paymentService = new PaymentSagaService(new InMemoryPaymentSagaRepository());
        WalletService walletService = new WalletService(new InMemoryWalletRepository());
        UUID accountId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        walletService.openWallet(
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                accountId,
                "Everyday wallet",
                "ZAR"
        );
        walletService.applyBalanceSnapshot(accountId, "ZAR", 1_250, Instant.parse("2026-06-20T12:00:00Z"));
        CountingApiEndpoint router = new CountingApiEndpoint(new ApiRouter(
                new PaymentApiAdapter(paymentService, request -> new FraudDecision(0.18, AuthPolicy.VOICE_OTP, true, "")),
                new WalletApiAdapter(walletService)
        ));
        InMemoryApiRequestLogSink logSink = new InMemoryApiRequestLogSink();
        ApiRuntime runtime = new ApiRuntime(
                router,
                StaticBearerTokenVerifier.of(Map.of(
                        "token-user-1", ApiPrincipal.of("user-1", "wallet:payment", "wallet:balance"),
                        "token-user-2", ApiPrincipal.of("user-2", "wallet:payment")
                )),
                new InMemoryApiRateLimiter(2),
                logSink
        );
        return new Fixture(runtime, router, logSink, accountId);
    }

    private static String paymentBody(UUID sagaId, long amount) {
        return "{"
                + "\"sagaId\":\"" + sagaId + "\","
                + "\"userId\":\"cccccccc-cccc-4ccc-8ccc-cccccccccccc\","
                + "\"fromAccountId\":\"dddddddd-dddd-4ddd-8ddd-dddddddddddd\","
                + "\"toAccountId\":\"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee\","
                + "\"amount\":" + amount + ","
                + "\"currency\":\"ZAR\""
                + "}";
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
            UUID accountId
    ) {
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}

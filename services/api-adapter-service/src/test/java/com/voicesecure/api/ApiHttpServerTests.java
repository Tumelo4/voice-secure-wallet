package com.voicesecure.api;

import com.voicesecure.payments.AuthPolicy;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.InMemoryPaymentSagaRepository;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.wallet.InMemoryWalletRepository;
import com.voicesecure.wallet.WalletService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class ApiHttpServerTests {
    public static void main(String[] args) throws Exception {
        TestCase[] tests = {
                new TestCase("local listener forwards wallet GET through runtime guards", ApiHttpServerTests::forwardsWalletGet),
                new TestCase("local listener forwards payment POST JSON", ApiHttpServerTests::forwardsPaymentPost),
                new TestCase("local listener preserves runtime rate-limit retry headers", ApiHttpServerTests::preservesRateLimitHeaders)
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
                    .header("Authorization", "Bearer token-user-1")
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
        UUID sagaId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID idempotencyKey = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        try (ApiHttpServer server = ApiHttpServer.start(fixture.runtime)) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(server.uri("/payments"))
                    .header("Authorization", "Bearer token-user-1")
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .header("X-Trace-Id", "trace-http-2")
                    .POST(HttpRequest.BodyPublishers.ofString(paymentBody(sagaId, 750)))
                    .build());

            assertEquals(202, response.statusCode(), "payment status");
            assertContains(response.body(), "\"state\":\"VOICE_VERIFICATION_PENDING\"", "payment state");
            assertEquals("/payments", fixture.logSink.entries().get(0).path(), "logged path");
        }
    }

    private static void preservesRateLimitHeaders() throws Exception {
        Fixture fixture = fixture(1);
        try (ApiHttpServer server = ApiHttpServer.start(fixture.runtime)) {
            HttpRequest first = HttpRequest.newBuilder(server.uri("/wallets/" + fixture.accountId + "/balance"))
                    .header("Authorization", "Bearer token-user-1")
                    .header("X-Trace-Id", "trace-http-3")
                    .GET()
                    .build();
            send(first);

            HttpResponse<String> limited = send(HttpRequest.newBuilder(server.uri("/wallets/" + fixture.accountId + "/balance"))
                    .header("Authorization", "Bearer token-user-1")
                    .header("X-Trace-Id", "trace-http-4")
                    .GET()
                    .build());

            assertEquals(429, limited.statusCode(), "rate-limit status");
            assertContains(limited.body(), "\"code\":\"RATE_LIMITED\"", "rate-limit body");
            assertEquals("2", limited.headers().firstValue("Retry-After").orElse(""), "retry header");
        }
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Fixture fixture(int rateLimit) {
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
        ApiRouter router = new ApiRouter(
                new PaymentApiAdapter(paymentService, request -> new FraudDecision(0.18, AuthPolicy.VOICE_OTP, true, "")),
                new WalletApiAdapter(walletService)
        );
        InMemoryApiRequestLogSink logSink = new InMemoryApiRequestLogSink();
        ApiRuntime runtime = new ApiRuntime(
                router,
                StaticBearerTokenVerifier.of(Map.of("token-user-1", "user-1")),
                new InMemoryApiRateLimiter(rateLimit),
                logSink
        );
        return new Fixture(runtime, logSink, accountId);
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

    private record Fixture(ApiRuntime runtime, InMemoryApiRequestLogSink logSink, UUID accountId) {
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

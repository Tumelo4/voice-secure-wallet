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

public final class ApiAdapterTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("payment POST starts saga through an HTTP adapter", ApiAdapterTests::paymentPostStartsSaga),
                new TestCase("payment POST maps idempotency conflict to 409", ApiAdapterTests::paymentPostMapsIdempotencyConflict),
                new TestCase("payment POST validates required trace header", ApiAdapterTests::paymentPostRequiresTraceHeader),
                new TestCase("wallet GET returns projected balance JSON", ApiAdapterTests::walletGetReturnsBalance),
                new TestCase("router returns JSON 404 for unknown route", ApiAdapterTests::routerReturnsJsonNotFound)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("API adapter tests passed: " + tests.length);
    }

    private static void paymentPostStartsSaga() {
        Fixture fixture = fixture();
        UUID idempotencyKey = UUID.fromString("33333333-3333-4333-8333-333333333333");

        ApiResponse response = fixture.router.handle(new ApiRequest(
                "POST",
                "/payments",
                Map.of("Idempotency-Key", idempotencyKey.toString(), "X-Trace-Id", "trace-api-1"),
                paymentBody(750)
        ));

        assertEquals(202, response.status(), "payment create status");
        assertContains(response.body(), "\"state\":\"VOICE_VERIFICATION_PENDING\"", "payment state");
        assertContains(response.body(), "\"traceId\":\"trace-api-1\"", "trace id should be echoed");
        assertEquals("application/json", response.headers().get("Content-Type"), "content type");
    }

    private static void paymentPostMapsIdempotencyConflict() {
        Fixture fixture = fixture();
        UUID idempotencyKey = UUID.fromString("44444444-4444-4444-8444-444444444444");
        Map<String, String> headers = Map.of("Idempotency-Key", idempotencyKey.toString(), "X-Trace-Id", "trace-api-2");

        fixture.router.handle(new ApiRequest("POST", "/payments", headers, paymentBody(750)));
        ApiResponse conflict = fixture.router.handle(new ApiRequest("POST", "/payments", headers, paymentBody(751)));

        assertEquals(409, conflict.status(), "idempotency conflict status");
        assertContains(conflict.body(), "\"code\":\"IDEMPOTENCY_CONFLICT\"", "idempotency conflict code");
        assertNotContains(conflict.body(), "Exception", "response must not leak exception type");
    }

    private static void paymentPostRequiresTraceHeader() {
        Fixture fixture = fixture();

        ApiResponse response = fixture.router.handle(new ApiRequest(
                "POST",
                "/payments",
                Map.of("Idempotency-Key", "55555555-5555-4555-8555-555555555555"),
                paymentBody(100)
        ));

        assertEquals(400, response.status(), "missing trace status");
        assertContains(response.body(), "\"code\":\"VALIDATION_FAILED\"", "validation code");
    }

    private static void walletGetReturnsBalance() {
        Fixture fixture = fixture();
        UUID accountId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        fixture.walletService.openWallet(
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                accountId,
                "Everyday wallet",
                "ZAR"
        );
        fixture.walletService.applyBalanceSnapshot(accountId, "ZAR", 1_250, Instant.parse("2026-06-20T12:00:00Z"));

        ApiResponse response = fixture.router.handle(new ApiRequest(
                "GET",
                "/wallets/" + accountId + "/balance",
                Map.of("X-Trace-Id", "trace-wallet-api"),
                ""
        ));

        assertEquals(200, response.status(), "wallet balance status");
        assertContains(response.body(), "\"accountId\":\"" + accountId + "\"", "account id");
        assertContains(response.body(), "\"balance\":1250", "balance");
        assertContains(response.body(), "\"currency\":\"ZAR\"", "currency");
    }

    private static void routerReturnsJsonNotFound() {
        Fixture fixture = fixture();

        ApiResponse response = fixture.router.handle(new ApiRequest("PATCH", "/unknown", Map.of(), "{}"));

        assertEquals(404, response.status(), "unknown route status");
        assertContains(response.body(), "\"code\":\"ROUTE_NOT_FOUND\"", "not found code");
        assertEquals("application/json", response.headers().get("Content-Type"), "content type");
    }

    private static String paymentBody(long amount) {
        return "{"
                + "\"sagaId\":\"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa\","
                + "\"userId\":\"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb\","
                + "\"fromAccountId\":\"cccccccc-cccc-4ccc-8ccc-cccccccccccc\","
                + "\"toAccountId\":\"dddddddd-dddd-4ddd-8ddd-dddddddddddd\","
                + "\"amount\":" + amount + ","
                + "\"currency\":\"ZAR\""
                + "}";
    }

    private static Fixture fixture() {
        PaymentSagaService paymentService = new PaymentSagaService(new InMemoryPaymentSagaRepository());
        WalletService walletService = new WalletService(new InMemoryWalletRepository());
        ApiRouter router = new ApiRouter(
                new PaymentApiAdapter(paymentService, request -> new FraudDecision(0.18, AuthPolicy.VOICE_OTP, true, "")),
                new WalletApiAdapter(walletService)
        );
        return new Fixture(router, walletService);
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

    private static void assertNotContains(String actual, String unexpected, String message) {
        if (actual.contains(unexpected)) {
            throw new AssertionError(message + ": did not expect to find " + unexpected + " in " + actual);
        }
    }

    private record Fixture(ApiRouter router, WalletService walletService) {
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}

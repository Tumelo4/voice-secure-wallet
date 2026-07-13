package com.voicesecure.api;

import com.voicesecure.beneficiaries.BeneficiaryRiskPolicy;
import com.voicesecure.beneficiaries.BeneficiaryService;
import com.voicesecure.beneficiaries.InMemoryBeneficiaryRepository;
import com.voicesecure.payments.AuthPolicy;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.InMemoryPaymentSagaRepository;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.wallet.InMemoryWalletRepository;
import com.voicesecure.wallet.WalletService;
import java.time.Instant;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

public final class ApiAdapterTests {
    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID SOURCE_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
    private static final UUID BENEFICIARY_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
    private static final UUID DESTINATION_ID = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("payment POST starts saga through an HTTP adapter", ApiAdapterTests::paymentPostStartsSaga),
                new TestCase("payment POST maps idempotency conflict to 409", ApiAdapterTests::paymentPostMapsIdempotencyConflict),
                new TestCase("payment POST validates required trace header", ApiAdapterTests::paymentPostRequiresTraceHeader),
                new TestCase("wallet GET returns projected balance JSON", ApiAdapterTests::walletGetReturnsBalance),
                new TestCase("wallet GET blocks cross-user account access", ApiAdapterTests::walletGetBlocksIdor),
                new TestCase("me accounts returns only authenticated customer accounts", ApiAdapterTests::meAccountsReturnsOwnedAccounts),
                new TestCase("payment rollout flag supports immediate rollback", ApiAdapterTests::paymentRolloutCanBeDisabled),
                new TestCase("voice result callback advances only the referenced payment", ApiAdapterTests::voiceResultAdvancesPayment),
                new TestCase("payment status blocks cross-customer access", ApiAdapterTests::paymentStatusBlocksCrossCustomerAccess),
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
        ApiResponse response = fixture.router.handle(new ApiRequest(
                "POST",
                "/v1/payments",
                authenticatedHeaders(USER_ID, "trace-api-1"),
                paymentBody(750)
        ));

        assertEquals(202, response.status(), "payment create status");
        assertContains(response.body(), "\"state\":\"AUTHORISATION_REQUIRED\"", "payment state");
        assertContains(response.body(), "\"paymentReference\":\"VSW-", "customer payment reference");
        assertNotContains(response.body(), "traceId", "internal trace id must not be exposed");
        assertNotContains(response.body(), "sagaId", "internal saga id must not be exposed");
        assertEquals("application/json", response.headers().get("Content-Type"), "content type");
    }

    private static void paymentPostMapsIdempotencyConflict() {
        Fixture fixture = fixture();
        Map<String, String> headers = authenticatedHeaders(USER_ID, "trace-api-2");

        fixture.router.handle(new ApiRequest("POST", "/v1/payments", headers, paymentBody(750)));
        ApiResponse conflict = fixture.router.handle(new ApiRequest("POST", "/v1/payments", headers, paymentBody(751)));

        assertEquals(409, conflict.status(), "idempotency conflict status");
        assertContains(conflict.body(), "\"code\":\"PAYMENT_CONFLICT\"", "idempotency conflict code");
        assertNotContains(conflict.body(), "Exception", "response must not leak exception type");
    }

    private static void paymentPostRequiresTraceHeader() {
        Fixture fixture = fixture();

        ApiResponse response = fixture.router.handle(new ApiRequest(
                "POST",
                "/v1/payments",
                Map.of(ApiSecurityContext.AUTHENTICATED_PRINCIPAL_HEADER, USER_ID.toString()),
                paymentBody(100)
        ));

        assertEquals(400, response.status(), "missing trace status");
        assertContains(response.body(), "\"code\":\"PAYMENT_INVALID\"", "validation code");
    }

    private static void walletGetReturnsBalance() {
        Fixture fixture = fixture();
        UUID accountId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        fixture.walletService.openWallet(USER_ID, accountId, "Everyday wallet", "ZAR");
        fixture.walletService.applyBalanceSnapshot(accountId, "ZAR", 1_250, Instant.parse("2026-06-20T12:00:00Z"));

        ApiResponse response = fixture.router.handle(new ApiRequest(
                "GET",
                "/wallets/" + accountId + "/balance",
                authenticatedHeaders(USER_ID, "trace-wallet-api"),
                ""
        ));

        assertEquals(200, response.status(), "wallet balance status");
        assertContains(response.body(), "\"accountId\":\"" + accountId + "\"", "account id");
        assertContains(response.body(), "\"balance\":1250", "balance");
        assertContains(response.body(), "\"currency\":\"ZAR\"", "currency");
    }

    private static void walletGetBlocksIdor() {
        Fixture fixture = fixture();
        UUID accountId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        fixture.walletService.openWallet(USER_ID, accountId, "Everyday wallet", "ZAR");
        ApiResponse response = fixture.router.handle(new ApiRequest(
                "GET",
                "/wallets/" + accountId + "/balance",
                authenticatedHeaders(UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"), "trace-idor"),
                ""
        ));
        assertEquals(404, response.status(), "cross-user balance must look unavailable");
        assertNotContains(response.body(), accountId.toString(), "IDOR response must not reveal account id");
    }

    private static void meAccountsReturnsOwnedAccounts() {
        Fixture fixture = fixture();
        ApiResponse response = fixture.router.handle(new ApiRequest(
                "GET",
                "/v1/me/accounts",
                authenticatedHeaders(USER_ID, "trace-accounts"),
                ""
        ));
        assertEquals(200, response.status(), "accounts status");
        assertContains(response.body(), SOURCE_ID.toString(), "owned source account");
        assertContains(response.body(), "•••• CCCC", "masked account number");
        assertNotContains(response.body(), BENEFICIARY_ID.toString(), "other customer's account");
        assertNotContains(response.body(), USER_ID.toString(), "owner id must not be exposed");
    }

    private static void routerReturnsJsonNotFound() {
        Fixture fixture = fixture();

        ApiResponse response = fixture.router.handle(new ApiRequest("PATCH", "/unknown", Map.of(), "{}"));

        assertEquals(404, response.status(), "unknown route status");
        assertContains(response.body(), "\"code\":\"ROUTE_NOT_FOUND\"", "not found code");
        assertEquals("application/json", response.headers().get("Content-Type"), "content type");
    }

    private static void paymentRolloutCanBeDisabled() {
        WalletService walletService = new WalletService(new InMemoryWalletRepository());
        BeneficiaryService beneficiaryService = beneficiaryService();
        ApiEndpoint endpoint = new PaymentApiAdapter(
                new PaymentSagaService(new InMemoryPaymentSagaRepository()),
                request -> new FraudDecision(0.18, AuthPolicy.VOICE_OTP, true, ""),
                walletService,
                beneficiaryService,
                PaymentRolloutPolicy.disabled()
        );
        ApiResponse response = endpoint.handle(new ApiRequest("POST", "/v1/payments", authenticatedHeaders(USER_ID, "trace-rollback"), "{}"));
        assertEquals(503, response.status(), "disabled rollout status");
        assertContains(response.body(), "PAYMENTS_TEMPORARILY_UNAVAILABLE", "rollback-safe error");
    }

    private static void voiceResultAdvancesPayment() {
        Fixture fixture = fixture();
        ApiResponse started = fixture.router.handle(new ApiRequest(
                "POST", "/v1/payments", authenticatedHeaders(USER_ID, "trace-voice-start"), paymentBody(750)));
        String reference = jsonString(started.body(), "paymentReference");
        ApiRequest callback = new ApiRequest(
                "POST", "/internal/payments/" + reference + "/voice-outcomes",
                Map.of(ApiSecurityContext.AUTHENTICATED_PRINCIPAL_HEADER, "voice-service", "X-Trace-Id", "trace-voice-result"),
                "{\"status\":\"APPROVED\",\"confidence\":\"0.94\",\"verificationId\":\"verification-1\"}");
        assertEquals(java.util.Set.of("voice:result"), fixture.paymentEndpoint.requiredScopes(callback), "voice callback scope");
        ApiResponse response = fixture.router.handle(callback);
        assertEquals(202, response.status(), "voice callback status");
        assertContains(response.body(), "\"state\":\"PROCESSING\"", "server-authoritative next state");
        ApiResponse duplicate = fixture.router.handle(callback);
        assertEquals(202, duplicate.status(), "duplicate voice callback status");
        assertContains(duplicate.body(), "\"state\":\"PROCESSING\"", "duplicate callback is idempotent");

        ApiResponse customerStatus = fixture.router.handle(new ApiRequest(
                "GET", "/v1/payments/" + reference, authenticatedHeaders(USER_ID, "trace-payment-status"), ""));
        assertEquals(200, customerStatus.status(), "owned status response");
        assertContains(customerStatus.body(), "PROCESSING", "owned payment state");
    }

    private static void paymentStatusBlocksCrossCustomerAccess() {
        Fixture fixture = fixture();
        ApiResponse started = fixture.router.handle(new ApiRequest(
                "POST", "/v1/payments", authenticatedHeaders(USER_ID, "trace-status-start"), paymentBody(750)));
        String reference = jsonString(started.body(), "paymentReference");
        ApiResponse response = fixture.router.handle(new ApiRequest(
                "GET", "/v1/payments/" + reference,
                authenticatedHeaders(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"), "trace-status-idor"), ""));
        assertEquals(403, response.status(), "cross-customer payment status");
        assertNotContains(response.body(), reference, "IDOR response must not expose payment reference");
    }

    private static String paymentBody(long amount) {
        return "{"
                + "\"sourceAccountId\":\"" + SOURCE_ID + "\","
                + "\"beneficiaryId\":\"" + BENEFICIARY_ID + "\","
                + "\"amount\":{\"value\":\"" + amount + ".00\",\"currency\":\"ZAR\"},"
                + "\"reference\":\"Dinner split\""
                + "}";
    }

    private static Map<String, String> authenticatedHeaders(UUID userId, String traceId) {
        return Map.of(
                ApiSecurityContext.AUTHENTICATED_PRINCIPAL_HEADER, userId.toString(),
                "X-Trace-Id", traceId,
                "Idempotency-Key", "44444444-4444-4444-8444-444444444444"
        );
    }

    private static String jsonString(String body, String field) {
        String marker = "\"" + field + "\":\"";
        int start = body.indexOf(marker);
        if (start < 0) throw new AssertionError("missing JSON field " + field + " in " + body);
        start += marker.length();
        return body.substring(start, body.indexOf('"', start));
    }

    private static Fixture fixture() {
        PaymentSagaService paymentService = new PaymentSagaService(new InMemoryPaymentSagaRepository());
        WalletService walletService = new WalletService(new InMemoryWalletRepository());
        walletService.openWallet(USER_ID, SOURCE_ID, "Everyday wallet", "ZAR");
        walletService.openWallet(UUID.randomUUID(), DESTINATION_ID, "Maya Nkosi", "ZAR");
        BeneficiaryService beneficiaryService = beneficiaryService();
        PaymentApiAdapter paymentEndpoint = new PaymentApiAdapter(
                paymentService, request -> new FraudDecision(0.18, AuthPolicy.VOICE_OTP, true, ""), walletService, beneficiaryService);
        ApiRouter router = new ApiRouter(
                paymentEndpoint,
                new WalletApiAdapter(walletService)
        );
        return new Fixture(router, walletService, paymentEndpoint);
    }

    private static BeneficiaryService beneficiaryService() {
        BeneficiaryService service = new BeneficiaryService(
                new InMemoryBeneficiaryRepository(), BeneficiaryRiskPolicy.standard(), Clock.systemUTC());
        service.registerVerified(BENEFICIARY_ID, USER_ID, DESTINATION_ID, "Maya Nkosi", "0000EEEE", "ZAR");
        return service;
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

    private record Fixture(ApiRouter router, WalletService walletService, PaymentApiAdapter paymentEndpoint) {
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}

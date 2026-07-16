package com.voicesecure.api;

import com.voicesecure.payments.AuthPolicy;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.InMemoryPaymentSagaRepository;
import com.voicesecure.payments.PaymentRequest;
import com.voicesecure.payments.PaymentSaga;
import com.voicesecure.payments.PaymentSagaService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class VoiceGatewayApiAdapterTests {
    private static final UUID CUSTOMER = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    public static void main(String[] args) {
        gatewayDerivesTrustedPaymentContext();
        gatewayBlocksCrossCustomerChallengeAccess();
        System.out.println("Voice gateway adapter tests passed: 2");
    }

    private static void gatewayDerivesTrustedPaymentContext() {
        Fixture fixture = fixture();
        ApiResponse issued = fixture.adapter.handle(request(CUSTOMER, "POST",
                "/v1/payments/" + fixture.reference + "/voice-challenge", "{}", Map.of()));
        assertEquals(201, issued.status(), "challenge status");
        String challengeId = field(issued.body(), "challengeId");
        String binding = field(issued.body(), "transactionBindingHash");
        if (binding.length() != 64) throw new AssertionError("binding must be SHA-256");

        String hostileBody = "{\"capturedAt\":\"2026-07-16T12:00:00Z\","
                + "\"paymentReference\":\"" + fixture.reference + "\","
                + "\"userId\":\"" + OTHER + "\",\"authPolicy\":\"DISABLED\",\"transactionAmountMinor\":1,"
                + "\"transactionBindingHash\":\"forged\",\"audio\":{\"contentBase64\":\"YQ==\",\"codec\":\"audio/mp4\",\"sampleRateHz\":44100}}";
        ApiResponse verified = fixture.adapter.handle(request(CUSTOMER, "POST",
                "/v1/voice/challenges/" + challengeId + "/verification", hostileBody,
                Map.of("Idempotency-Key", "idem-voice-1")));

        assertEquals(200, verified.status(), "verification status");
        VoiceGatewayClient.Verification forwarded = fixture.voice.lastVerification;
        assertEquals(CUSTOMER, forwarded.customerId(), "server principal");
        assertEquals(75000L, forwarded.transactionAmountMinor(), "server amount");
        assertEquals("VOICE_OTP", forwarded.authPolicy(), "server auth policy");
        assertEquals(binding, forwarded.transactionBindingHash(), "server binding");
        assertEquals(fixture.reference, forwarded.paymentReference(), "server payment reference");
    }

    private static void gatewayBlocksCrossCustomerChallengeAccess() {
        Fixture fixture = fixture();
        ApiResponse denied = fixture.adapter.handle(request(OTHER, "POST",
                "/v1/payments/" + fixture.reference + "/voice-challenge", "{}", Map.of()));
        assertEquals(403, denied.status(), "cross-customer issue");
    }

    private static Fixture fixture() {
        PaymentSagaService payments = new PaymentSagaService(new InMemoryPaymentSagaRepository());
        PaymentSaga saga = payments.start(new PaymentRequest(UUID.randomUUID(), UUID.randomUUID(), CUSTOMER,
                UUID.randomUUID(), UUID.randomUUID(), 75000, "ZAR", "trace"),
                new FraudDecision(0.1, AuthPolicy.VOICE_OTP, true, ""));
        InMemoryPaymentReferenceRegistry references = new InMemoryPaymentReferenceRegistry();
        String reference = references.referenceFor(saga.sagaId(), CUSTOMER);
        FakeVoiceClient voice = new FakeVoiceClient();
        return new Fixture(new VoiceGatewayApiAdapter(payments, references, voice), voice, reference);
    }

    private static ApiRequest request(UUID customer, String method, String path, String body, Map<String, String> extra) {
        java.util.HashMap<String, String> headers = new java.util.HashMap<>(extra);
        headers.put(ApiSecurityContext.AUTHENTICATED_PRINCIPAL_HEADER, customer.toString());
        return new ApiRequest(method, path, headers, body);
    }

    private static String field(String json, String name) {
        return ApiJson.stringField(json, name);
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + " but got " + actual);
    }

    private static final class FakeVoiceClient implements VoiceGatewayClient {
        private final UUID challengeId = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
        private Verification lastVerification;
        @Override public Challenge issueChallenge(UUID customerId, String phrase, String binding) {
            return new Challenge(challengeId, phrase, Instant.parse("2026-07-16T12:01:00Z"));
        }
        @Override public String verify(Verification request) {
            lastVerification = request;
            return "{\"verificationId\":\"v-1\",\"status\":\"VERIFIED\",\"fallbackRequested\":false,\"reason\":\"accepted\"}";
        }
    }

    private record Fixture(VoiceGatewayApiAdapter adapter, FakeVoiceClient voice, String reference) { }
}

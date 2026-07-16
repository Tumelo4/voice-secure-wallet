package com.voicesecure.api;

import com.voicesecure.payments.PaymentSaga;
import com.voicesecure.payments.PaymentSagaService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class VoiceGatewayApiAdapter implements ApiEndpoint {
    private static final Set<String> SCOPES = Set.of("wallet:payment");
    private final PaymentSagaService payments;
    private final PaymentReferenceRegistry references;
    private final VoiceGatewayClient voice;

    public VoiceGatewayApiAdapter(PaymentSagaService payments, PaymentReferenceRegistry references, VoiceGatewayClient voice) {
        this.payments = Objects.requireNonNull(payments);
        this.references = Objects.requireNonNull(references);
        this.voice = Objects.requireNonNull(voice);
    }

    @Override
    public boolean supports(ApiRequest request) {
        return "POST".equals(request.method()) && (paymentReference(request.path()) != null || challengeId(request.path()) != null);
    }

    @Override
    public ApiResponse handle(ApiRequest request) {
        try {
            UUID customerId = UUID.fromString(ApiSecurityContext.requirePrincipal(request));
            String reference = paymentReference(request.path());
            if (reference != null) return issue(reference, customerId);
            return verify(Objects.requireNonNull(challengeId(request.path())), customerId, request);
        } catch (SecurityException error) {
            return ApiResponse.error(403, "VOICE_NOT_AUTHORIZED", "You are not authorized for this voice challenge.");
        } catch (IllegalArgumentException error) {
            return ApiResponse.error(400, "VOICE_REQUEST_INVALID", "Review the voice request and try again.");
        } catch (IllegalStateException error) {
            return ApiResponse.error(502, "VOICE_SERVICE_UNAVAILABLE", "Voice verification is temporarily unavailable. Use fallback authentication.");
        }
    }

    @Override
    public Set<String> requiredScopes(ApiRequest request) { return supports(request) ? SCOPES : Set.of(); }

    private ApiResponse issue(String reference, UUID customerId) {
        PaymentReferenceRegistry.RegisteredPayment registered = references.find(reference)
                .orElseThrow(() -> new IllegalArgumentException("payment not found"));
        if (!registered.customerId().equals(customerId)) throw new SecurityException("payment ownership failed");
        PaymentSaga saga = payments.find(registered.sagaId());
        String binding = binding(reference, saga);
        String phrase = "Confirm payment";
        VoiceGatewayClient.Challenge result = voice.issueChallenge(customerId, phrase, binding);
        return ApiResponse.json(201, "{" +
                "\"challengeId\":" + ApiJson.quote(result.challengeId().toString()) + "," +
                "\"phrase\":" + ApiJson.quote(result.phrase()) + "," +
                "\"expiresAt\":" + ApiJson.quote(result.expiresAt().toString()) + "," +
                "\"authPolicy\":" + ApiJson.quote(saga.authPolicy().name()) + "," +
                "\"transactionAmountMinor\":" + saga.amount() + "," +
                "\"transactionBindingHash\":" + ApiJson.quote(binding) + "}");
    }

    private ApiResponse verify(UUID challengeId, UUID customerId, ApiRequest request) {
        String paymentReference = ApiJson.stringField(request.body(), "paymentReference");
        PaymentReferenceRegistry.RegisteredPayment registered = references.find(paymentReference)
                .orElseThrow(() -> new IllegalArgumentException("payment not found"));
        if (!registered.customerId().equals(customerId)) throw new SecurityException("payment ownership failed");
        PaymentSaga saga = payments.find(registered.sagaId());
        String transactionBinding = binding(paymentReference, saga);
        Instant capturedAt = Instant.parse(ApiJson.stringField(request.body(), "capturedAt"));
        String idempotencyKey = requiredHeader(request, "Idempotency-Key");
        String response = voice.verify(new VoiceGatewayClient.Verification(
                customerId, challengeId, paymentReference, transactionBinding,
                saga.authPolicy().name(), saga.amount(), capturedAt, ApiJson.objectField(request.body(), "audio"), idempotencyKey));
        return ApiResponse.json(200, response);
    }

    private static String binding(String reference, PaymentSaga saga) {
        String material = reference + ":" + saga.sagaId() + ":" + saga.userId() + ":" + saga.amount() + ":" + saga.currency();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String requiredHeader(ApiRequest request, String name) {
        String value = request.header(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing header");
        return value;
    }

    private static String paymentReference(String path) { return segment(path, "/v1/payments/", "/voice-challenge"); }
    private static UUID challengeId(String path) {
        String value = segment(path, "/v1/voice/challenges/", "/verification");
        return value == null ? null : UUID.fromString(value);
    }
    private static String segment(String path, String prefix, String suffix) {
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) return null;
        String value = path.substring(prefix.length(), path.length() - suffix.length());
        return value.isBlank() || value.contains("/") ? null : value;
    }
}

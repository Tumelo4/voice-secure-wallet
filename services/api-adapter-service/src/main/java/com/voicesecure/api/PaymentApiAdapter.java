package com.voicesecure.api;

import com.voicesecure.payments.PaymentException;
import com.voicesecure.payments.PaymentRequest;
import com.voicesecure.payments.PaymentSaga;
import com.voicesecure.payments.PaymentSagaService;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;

public final class PaymentApiAdapter implements ApiEndpoint {
    private static final Set<String> REQUIRED_SCOPES = Set.of("wallet:payment");

    private final PaymentSagaService paymentSagaService;
    private final FraudDecisionProvider fraudDecisionProvider;

    public PaymentApiAdapter(PaymentSagaService paymentSagaService, FraudDecisionProvider fraudDecisionProvider) {
        this.paymentSagaService = Objects.requireNonNull(paymentSagaService, "paymentSagaService");
        this.fraudDecisionProvider = Objects.requireNonNull(fraudDecisionProvider, "fraudDecisionProvider");
    }

    @Override
    public boolean supports(ApiRequest request) {
        return "POST".equals(request.method()) && "/payments".equals(request.path());
    }

    @Override
    public ApiResponse handle(ApiRequest request) {
        try {
            PaymentRequest paymentRequest = new PaymentRequest(
                    uuidField(request.body(), "sagaId"),
                    requiredUuidHeader(request, "Idempotency-Key"),
                    uuidField(request.body(), "userId"),
                    uuidField(request.body(), "fromAccountId"),
                    uuidField(request.body(), "toAccountId"),
                    ApiJson.longField(request.body(), "amount"),
                    ApiJson.stringField(request.body(), "currency"),
                    requiredHeader(request, "X-Trace-Id")
            );
            PaymentSaga saga = paymentSagaService.start(paymentRequest, fraudDecisionProvider.assess(paymentRequest));
            return ApiResponse.json(202, paymentBody(saga));
        } catch (PaymentException ex) {
            if (ex.getMessage().contains("idempotency key reused")) {
                return ApiResponse.error(409, "IDEMPOTENCY_CONFLICT", ex.getMessage());
            }
            return ApiResponse.error(400, "VALIDATION_FAILED", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(400, "VALIDATION_FAILED", ex.getMessage());
        }
    }

    @Override
    public Set<String> requiredScopes(ApiRequest request) {
        return supports(request) ? REQUIRED_SCOPES : Set.of();
    }

    private static UUID uuidField(String json, String field) {
        return UUID.fromString(ApiJson.stringField(json, field));
    }

    private static UUID requiredUuidHeader(ApiRequest request, String name) {
        return UUID.fromString(requiredHeader(request, name));
    }

    private static String requiredHeader(ApiRequest request, String name) {
        String value = request.header(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing required header: " + name);
        }
        return value;
    }

    private static String paymentBody(PaymentSaga saga) {
        return "{"
                + "\"sagaId\":" + ApiJson.quote(saga.sagaId().toString()) + ","
                + "\"state\":" + ApiJson.quote(saga.state().name()) + ","
                + "\"traceId\":" + ApiJson.quote(saga.traceId()) + ","
                + "\"authPolicy\":" + ApiJson.quote(saga.authPolicy().name()) + ","
                + "\"eventCount\":" + saga.events().size()
                + "}";
    }
}

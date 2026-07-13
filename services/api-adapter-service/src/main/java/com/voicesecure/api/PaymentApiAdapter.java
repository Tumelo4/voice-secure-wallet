package com.voicesecure.api;

import com.voicesecure.beneficiaries.Beneficiary;
import com.voicesecure.beneficiaries.BeneficiaryException;
import com.voicesecure.beneficiaries.BeneficiaryService;
import com.voicesecure.payments.PaymentException;
import com.voicesecure.payments.PaymentRequest;
import com.voicesecure.payments.PaymentSaga;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.payments.Money;
import com.voicesecure.payments.VoiceOutcome;
import com.voicesecure.payments.VoiceOutcomeStatus;
import com.voicesecure.wallet.WalletException;
import com.voicesecure.wallet.WalletService;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;

public final class PaymentApiAdapter implements ApiEndpoint {
    private static final Set<String> REQUIRED_SCOPES = Set.of("wallet:payment");
    private static final Set<String> VOICE_RESULT_SCOPES = Set.of("voice:result");

    private final PaymentSagaService paymentSagaService;
    private final FraudDecisionProvider fraudDecisionProvider;
    private final WalletService walletService;
    private final BeneficiaryService beneficiaryService;
    private final PaymentRolloutPolicy rolloutPolicy;
    private final PaymentReferenceRegistry paymentReferences;

    public PaymentApiAdapter(
            PaymentSagaService paymentSagaService,
            FraudDecisionProvider fraudDecisionProvider,
            WalletService walletService,
            BeneficiaryService beneficiaryService
    ) {
        this(paymentSagaService, fraudDecisionProvider, walletService, beneficiaryService,
                PaymentRolloutPolicy.enabled(), new InMemoryPaymentReferenceRegistry());
    }

    public PaymentApiAdapter(
            PaymentSagaService paymentSagaService,
            FraudDecisionProvider fraudDecisionProvider,
            WalletService walletService,
            BeneficiaryService beneficiaryService,
            PaymentRolloutPolicy rolloutPolicy
    ) {
        this(paymentSagaService, fraudDecisionProvider, walletService, beneficiaryService,
                rolloutPolicy, new InMemoryPaymentReferenceRegistry());
    }

    public PaymentApiAdapter(
            PaymentSagaService paymentSagaService,
            FraudDecisionProvider fraudDecisionProvider,
            WalletService walletService,
            BeneficiaryService beneficiaryService,
            PaymentRolloutPolicy rolloutPolicy,
            PaymentReferenceRegistry paymentReferences
    ) {
        this.paymentSagaService = Objects.requireNonNull(paymentSagaService, "paymentSagaService");
        this.fraudDecisionProvider = Objects.requireNonNull(fraudDecisionProvider, "fraudDecisionProvider");
        this.walletService = Objects.requireNonNull(walletService, "walletService");
        this.beneficiaryService = Objects.requireNonNull(beneficiaryService, "beneficiaryService");
        this.rolloutPolicy = Objects.requireNonNull(rolloutPolicy, "rolloutPolicy");
        this.paymentReferences = Objects.requireNonNull(paymentReferences, "paymentReferences");
    }

    @Override
    public boolean supports(ApiRequest request) {
        return ("POST".equals(request.method()) && "/v1/payments".equals(request.path()))
                || paymentReference(request.path(), "/internal/payments/", "/voice-outcomes") != null
                || ("GET".equals(request.method()) && paymentReference(request.path(), "/v1/payments/", "") != null);
    }

    @Override
    public ApiResponse handle(ApiRequest request) {
        if (!rolloutPolicy.customerIntentEnabled()) {
            return ApiResponse.error(503, "PAYMENTS_TEMPORARILY_UNAVAILABLE", "Payments are temporarily unavailable.");
        }
        try {
            String voiceReference = paymentReference(request.path(), "/internal/payments/", "/voice-outcomes");
            if (voiceReference != null) {
                return recordVoiceOutcome(voiceReference, request);
            }
            String statusReference = "GET".equals(request.method())
                    ? paymentReference(request.path(), "/v1/payments/", "") : null;
            if (statusReference != null) {
                return paymentStatus(statusReference, request);
            }
            UUID userId = UUID.fromString(ApiSecurityContext.requirePrincipal(request));
            UUID sourceAccountId = uuidField(request.body(), "sourceAccountId");
            UUID beneficiaryId = uuidField(request.body(), "beneficiaryId");
            requireOwnedSourceAccount(userId, sourceAccountId);
            Beneficiary beneficiary = beneficiaryService.requireAvailable(userId, beneficiaryId);
            UUID beneficiaryAccountId = beneficiary.destinationAccountId();
            walletService.account(beneficiaryAccountId);
            String currency = normalizedCurrency(request.body());
            Money money = Money.parse(ApiJson.stringField(request.body(), "value"), currency);
            long amountMinor = money.minorUnits();
            String reference = paymentReference(request.body());
            UUID idempotencyKey = requiredUuidHeader(request, "Idempotency-Key");
            PaymentRequest paymentRequest = new PaymentRequest(
                    UUID.randomUUID(),
                    idempotencyKey,
                    userId,
                    sourceAccountId,
                    beneficiaryAccountId,
                    amountMinor,
                    currency,
                    requiredHeader(request, "X-Trace-Id")
            );
            PaymentSaga saga = paymentSagaService.start(paymentRequest, fraudDecisionProvider.assess(paymentRequest));
            return ApiResponse.json(202, paymentBody(saga, paymentReferences.referenceFor(saga.sagaId(), userId)));
        } catch (PaymentException ex) {
            if (ex.getMessage().contains("idempotency key reused")) {
                return ApiResponse.error(409, "PAYMENT_CONFLICT", "This payment request conflicts with an earlier request.");
            }
            return ApiResponse.error(400, "PAYMENT_INVALID", "Review the payment details and try again.");
        } catch (WalletException ex) {
            return ApiResponse.error(404, "PAYMENT_ACCOUNT_UNAVAILABLE", "One of the selected accounts is unavailable.");
        } catch (BeneficiaryException ex) {
            return ApiResponse.error(404, "PAYMENT_BENEFICIARY_UNAVAILABLE", "The selected beneficiary is unavailable.");
        } catch (SecurityException ex) {
            return ApiResponse.error(403, "PAYMENT_NOT_AUTHORIZED", "You are not authorized to use the selected account.");
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(400, "PAYMENT_INVALID", "Review the payment details and try again.");
        }
    }

    @Override
    public Set<String> requiredScopes(ApiRequest request) {
        if (paymentReference(request.path(), "/internal/payments/", "/voice-outcomes") != null) {
            return VOICE_RESULT_SCOPES;
        }
        return supports(request) ? REQUIRED_SCOPES : Set.of();
    }

    private static UUID uuidField(String json, String field) {
        return UUID.fromString(ApiJson.stringField(json, field));
    }

    private void requireOwnedSourceAccount(UUID userId, UUID accountId) {
        if (!walletService.ownsAccount(userId, accountId)) {
            throw new SecurityException("source account ownership failed");
        }
    }

    private static String normalizedCurrency(String json) {
        String currency = ApiJson.stringField(json, "currency").trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an ISO 4217 code");
        }
        return currency;
    }

    private static String paymentReference(String json) {
        String reference = ApiJson.stringField(json, "reference").trim();
        if (reference.isEmpty() || reference.length() > 80) {
            throw new IllegalArgumentException("reference must contain 1 to 80 characters");
        }
        return reference;
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

    private ApiResponse recordVoiceOutcome(String reference, ApiRequest request) {
        PaymentReferenceRegistry.RegisteredPayment payment = paymentReferences.find(reference)
                .orElseThrow(() -> new PaymentException("payment reference not found"));
        VoiceOutcomeStatus status = VoiceOutcomeStatus.valueOf(
                ApiJson.stringField(request.body(), "status").trim().toUpperCase(Locale.ROOT));
        double confidence = Double.parseDouble(ApiJson.stringField(request.body(), "confidence"));
        String verificationId = ApiJson.stringField(request.body(), "verificationId").trim();
        if (verificationId.isEmpty()) throw new IllegalArgumentException("verification id is required");
        PaymentSaga saga = paymentSagaService.recordVoiceOutcome(
                payment.sagaId(), new VoiceOutcome(status, confidence, "verification:" + verificationId));
        return ApiResponse.json(202, paymentBody(saga, reference));
    }

    private ApiResponse paymentStatus(String reference, ApiRequest request) {
        UUID customerId = UUID.fromString(ApiSecurityContext.requirePrincipal(request));
        PaymentReferenceRegistry.RegisteredPayment payment = paymentReferences.find(reference)
                .orElseThrow(() -> new PaymentException("payment reference not found"));
        if (!payment.customerId().equals(customerId)) throw new SecurityException("payment ownership failed");
        return ApiResponse.json(200, paymentBody(paymentSagaService.find(payment.sagaId()), reference));
    }

    private static String paymentBody(PaymentSaga saga, String reference) {
        return "{"
                + "\"paymentReference\":" + ApiJson.quote(reference) + ","
                + "\"state\":" + ApiJson.quote(publicState(saga)) + ","
                + "\"authPolicy\":" + ApiJson.quote(saga.authPolicy().name()) + ","
                + "\"message\":\"Payment submitted for secure verification.\""
                + "}";
    }

    private static String paymentReference(String path, String prefix, String suffix) {
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) return null;
        String value = path.substring(prefix.length(), path.length() - suffix.length());
        return value.isBlank() || value.contains("/") ? null : value;
    }

    private static String publicState(PaymentSaga saga) {
        return switch (saga.state()) {
            case INITIATED, FRAUD_CHECK_PENDING, VOICE_VERIFICATION_PENDING, VOICE_FALLBACK_PENDING -> "AUTHORISATION_REQUIRED";
            case FUNDS_RESERVING, FUNDS_RESERVED, LEDGER_COMMITTING, LEDGER_COMMITTED, COMPLETING -> "PROCESSING";
            case COMPLETED -> "COMPLETED";
            case UNKNOWN_EXTERNAL_STATUS, RECONCILIATION_REQUIRED, MANUAL_REVIEW -> "REVIEW_REQUIRED";
            default -> "FAILED";
        };
    }
}

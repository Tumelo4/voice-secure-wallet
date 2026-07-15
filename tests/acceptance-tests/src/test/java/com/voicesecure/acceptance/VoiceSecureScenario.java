package com.voicesecure.acceptance;

import com.voicesecure.compliance.ComplianceProfile;
import com.voicesecure.compliance.ComplianceService;
import com.voicesecure.compliance.InMemoryComplianceRepository;
import com.voicesecure.events.EventEnvelope;
import com.voicesecure.events.EventEnvelopeFactory;
import com.voicesecure.events.EventTopic;
import com.voicesecure.fraud.FraudAssessment;
import com.voicesecure.fraud.FraudService;
import com.voicesecure.fraud.FraudTransactionRequest;
import com.voicesecure.fraud.VelocityTracker;
import com.voicesecure.ledger.LedgerBatch;
import com.voicesecure.ledger.application.LedgerService;
import com.voicesecure.ledger.infrastructure.InMemoryLedgerRepository;
import com.voicesecure.notifications.DeterministicOtpGenerator;
import com.voicesecure.notifications.InMemoryNotificationRepository;
import com.voicesecure.notifications.NotificationChannel;
import com.voicesecure.notifications.NotificationDelivery;
import com.voicesecure.notifications.NotificationService;
import com.voicesecure.payments.FallbackMethod;
import com.voicesecure.payments.FallbackOutcome;
import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.InMemoryPaymentSagaRepository;
import com.voicesecure.payments.PaymentRequest;
import com.voicesecure.payments.PaymentSaga;
import com.voicesecure.payments.PaymentSagaService;
import com.voicesecure.payments.VoiceOutcome;
import com.voicesecure.payments.VoiceOutcomeStatus;
import com.voicesecure.wallet.InMemoryWalletRepository;
import com.voicesecure.wallet.WalletService;
import java.time.Instant;
import java.util.UUID;

final class VoiceSecureScenario {
    private static final String CURRENCY = "ZAR";

    private final UUID userId = UUID.randomUUID();
    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID destinationAccountId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private final ComplianceService complianceService;
    private final FraudService fraudService;
    private final InMemoryLedgerRepository ledgerRepository;
    private final LedgerService ledgerService;
    private final PaymentSagaService paymentService;
    private final WalletService walletService;
    private final InMemoryNotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final String nationalId;
    private PaymentRequest currentRequest;
    private PaymentSaga currentSaga;
    private int walletProjectionCount;

    private VoiceSecureScenario(long openingBalance, boolean pepCustomer) {
        InMemoryComplianceRepository complianceRepository = new InMemoryComplianceRepository();
        this.complianceService = new ComplianceService(complianceRepository);
        this.nationalId = pepCustomer ? "PEP-123" : "CLEAR-123";
        if (pepCustomer) {
            complianceService.addPep(nationalId, "board member on PEP list");
        }
        this.fraudService = new FraudService(complianceService, new VelocityTracker());
        this.ledgerRepository = new InMemoryLedgerRepository();
        this.ledgerService = new LedgerService(ledgerRepository);
        this.paymentService = new PaymentSagaService(new InMemoryPaymentSagaRepository());
        this.walletService = new WalletService(new InMemoryWalletRepository());
        this.notificationRepository = new InMemoryNotificationRepository();
        this.notificationService = new NotificationService(notificationRepository, new DeterministicOtpGenerator("123456"));

        ledgerService.createAccount(sourceAccountId, CURRENCY, openingBalance);
        ledgerService.createAccount(destinationAccountId, CURRENCY, 0);
        walletService.openWallet(userId, sourceAccountId, "Everyday wallet", CURRENCY);
        walletService.openWallet(userId, destinationAccountId, "Merchant wallet", CURRENCY);
        walletService.applyBalanceSnapshot(sourceAccountId, CURRENCY, openingBalance, Instant.parse("2026-07-01T08:00:00Z"));
        walletService.applyBalanceSnapshot(destinationAccountId, CURRENCY, 0, Instant.parse("2026-07-01T08:00:00Z"));
    }

    static VoiceSecureScenario givenTrustedCustomerWithWallets(long openingBalance) {
        return new VoiceSecureScenario(openingBalance, false);
    }

    static VoiceSecureScenario givenPepCustomerWithWallets(long openingBalance) {
        return new VoiceSecureScenario(openingBalance, true);
    }

    VoiceSecureScenario whenHighValuePaymentNeedsOtpFallback(long amount) {
        PaymentRequest request = newPaymentRequest(amount);
        FraudAssessment assessment = screenPayment(request, 500);
        currentSaga = paymentService.start(request, paymentDecision(assessment));
        paymentService.recordVoiceOutcome(currentSaga.sagaId(), new VoiceOutcome(VoiceOutcomeStatus.TIMEOUT, 0.0, "voice timed out"));
        notificationService.consume(voiceFallbackEvent(amount));
        currentSaga = paymentService.recordFallbackOutcome(
                currentSaga.sagaId(),
                new FallbackOutcome(FallbackMethod.OTP, true, "OTP verified")
        );
        return this;
    }

    VoiceSecureScenario thenOtpFallbackIsDelivered() {
        boolean delivered = notificationRepository.deliveries().stream()
                .anyMatch(delivery -> delivery.channel() == NotificationChannel.OTP
                        && delivery.sourceEventType().equals("voice.fallback_requested"));
        if (!delivered) {
            throw new AssertionError("expected OTP fallback notification");
        }
        return this;
    }

    PaymentOutcome thenFallbackPaymentCompletes() {
        return completeApprovedSaga();
    }

    PaymentOutcome whenPaymentIsScreened(long amount) {
        PaymentRequest request = newPaymentRequest(amount);
        FraudAssessment assessment = screenPayment(request, 5_000);
        currentSaga = paymentService.start(request, paymentDecision(assessment));
        return outcome();
    }

    PaymentOutcome whenVoiceApprovedPaymentCompletes(long amount) {
        PaymentRequest request = newPaymentRequest(amount);
        FraudAssessment assessment = screenPayment(request, 5_000);
        currentSaga = paymentService.start(request, paymentDecision(assessment));
        currentSaga = paymentService.recordVoiceOutcome(
                currentSaga.sagaId(),
                new VoiceOutcome(VoiceOutcomeStatus.APPROVED, 0.98, "voice matched")
        );
        return completeApprovedSaga();
    }

    private PaymentOutcome completeApprovedSaga() {
        currentSaga = paymentService.markFundsReserved(currentSaga.sagaId());
        LedgerBatch batch = ledgerService.transfer(
                currentSaga.sagaId(),
                currentRequest.idempotencyKey(),
                sourceAccountId,
                destinationAccountId,
                currentRequest.amount(),
                CURRENCY
        );
        batch.entries().forEach(entry -> {
            walletService.projectLedgerEntry(entry.toEnvelope(currentRequest.traceId()));
            walletProjectionCount++;
        });
        currentSaga = paymentService.startLedgerCommit(currentSaga.sagaId());
        currentSaga = paymentService.completeLedgerCommit(currentSaga.sagaId());
        currentSaga = paymentService.complete(currentSaga.sagaId());
        notificationService.consume(paymentCompletedEvent());
        return outcome();
    }

    private PaymentRequest newPaymentRequest(long amount) {
        currentRequest = new PaymentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                userId,
                sourceAccountId,
                destinationAccountId,
                amount,
                CURRENCY,
                "trace-bdd-" + UUID.randomUUID()
        );
        return currentRequest;
    }

    private FraudAssessment screenPayment(PaymentRequest request, long highValueThreshold) {
        return fraudService.evaluate(new FraudTransactionRequest(
                request.sagaId(),
                new ComplianceProfile(userId, "Nomsa Dlamini", nationalId, "ZA", request.amount()),
                request.amount(),
                request.currency(),
                deviceId,
                0.99,
                48,
                highValueThreshold,
                Instant.parse("2026-07-01T08:15:00Z")
        ));
    }

    private FraudDecision paymentDecision(FraudAssessment assessment) {
        return new FraudDecision(
                assessment.score(),
                com.voicesecure.payments.AuthPolicy.valueOf(assessment.authPolicy().name()),
                assessment.approved(),
                assessment.reason()
        );
    }

    private EventEnvelope voiceFallbackEvent(long amount) {
        String payload = "{"
                + "\"userId\":\"" + userId + "\","
                + "\"method\":\"OTP\","
                + "\"transactionAmount\":" + amount
                + "}";
        return EventEnvelopeFactory.create(
                EventTopic.VOICE,
                userId,
                "VoiceVerification",
                "voice.fallback_requested",
                Instant.parse("2026-07-01T08:16:00Z"),
                currentRequest.traceId(),
                payload
        );
    }

    private EventEnvelope paymentCompletedEvent() {
        String payload = "{"
                + "\"userId\":\"" + userId + "\","
                + "\"amount\":" + currentRequest.amount() + ","
                + "\"currency\":\"" + CURRENCY + "\""
                + "}";
        return EventEnvelopeFactory.create(
                EventTopic.PAYMENTS,
                currentSaga.sagaId(),
                "Payment",
                "payment.completed",
                Instant.parse("2026-07-01T08:17:00Z"),
                currentRequest.traceId(),
                payload
        );
    }

    private PaymentOutcome outcome() {
        return new PaymentOutcome(
                currentSaga.state().name(),
                walletService.balance(sourceAccountId).balance(),
                walletService.balance(destinationAccountId).balance(),
                ledgerService.reconcile().totalSignedAmount(),
                ledgerRepository.entries().size(),
                walletProjectionCount,
                notificationRepository.deliveries().size()
        );
    }
}

package com.voicesecure.payments;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PaymentSaga {
    private static final String PAYMENT_INITIATED = "payment.initiated";
    private static final String PAYMENT_FRAUD_CHECK_REQUESTED = "payment.fraud_check_requested";
    private static final String PAYMENT_FRAUD_APPROVED = "payment.fraud_approved";
    private static final String PAYMENT_FRAUD_REJECTED = "payment.fraud_rejected";
    private static final String PAYMENT_VOICE_REQUESTED = "payment.voice_requested";
    private static final String PAYMENT_VOICE_APPROVED = "payment.voice_approved";
    private static final String PAYMENT_VOICE_REJECTED = "payment.voice_rejected";
    private static final String PAYMENT_VOICE_TIMEOUT = "payment.voice_timeout";
    private static final String PAYMENT_VOICE_FALLBACK_REQUESTED = "payment.voice_fallback_requested";
    private static final String PAYMENT_VOICE_FALLBACK_VERIFIED = "payment.voice_fallback_verified";
    private static final String PAYMENT_VOICE_FALLBACK_FAILED = "payment.voice_fallback_failed";
    private static final String PAYMENT_FUNDS_RESERVED = "payment.funds_reserved";
    private static final String PAYMENT_FUNDS_RESERVATION_FAILED = "payment.funds_reservation_failed";
    private static final String PAYMENT_LEDGER_COMMITTING = "payment.ledger_committing";
    private static final String PAYMENT_LEDGER_COMMITTED = "payment.ledger_committed";
    private static final String PAYMENT_LEDGER_COMMIT_FAILED = "payment.ledger_commit_failed";
    private static final String PAYMENT_COMPLETING = "payment.completing";
    private static final String PAYMENT_COMPLETED = "payment.completed";
    private static final String PAYMENT_COMPENSATION_IN_PROGRESS = "payment.compensation_initiated";
    private static final String PAYMENT_COMPENSATED = "payment.compensated";
    private static final String PAYMENT_COMPENSATION_FAILED = "payment.compensation_failed";

    private final UUID sagaId;
    private final UUID idempotencyKey;
    private final UUID userId;
    private final UUID fromAccountId;
    private final UUID toAccountId;
    private final long amount;
    private final String currency;
    private final String traceId;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private double fraudScore;
    private AuthPolicy authPolicy;
    private FallbackMethod fallbackMethod;
    private PaymentSagaState state;
    private final List<PaymentSagaState> stateHistory = new ArrayList<>();
    private final List<PaymentEvent> events = new ArrayList<>();

    private PaymentSaga(PaymentRequest request) {
        this(request, new ConstructionTimestamps(Instant.now()));
    }

    private PaymentSaga(PaymentRequest request, ConstructionTimestamps timestamps) {
        this(
                request.sagaId(),
                request.idempotencyKey(),
                request.userId(),
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                request.currency(),
                request.traceId(),
                timestamps.createdAt(),
                timestamps.updatedAt(),
                null,
                0.0,
                null,
                null,
                PaymentSagaState.INITIATED,
                List.of(PaymentSagaState.INITIATED),
                List.of()
        );
        emit(PAYMENT_INITIATED, payload("amount", Long.toString(amount)));
        transition(PaymentSagaState.FRAUD_CHECK_PENDING, PAYMENT_FRAUD_CHECK_REQUESTED, payload("currency", currency));
    }

    private PaymentSaga(
            UUID sagaId,
            UUID idempotencyKey,
            UUID userId,
            UUID fromAccountId,
            UUID toAccountId,
            long amount,
            String currency,
            String traceId,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt,
            double fraudScore,
            AuthPolicy authPolicy,
            FallbackMethod fallbackMethod,
            PaymentSagaState state,
            List<PaymentSagaState> stateHistory,
            List<PaymentEvent> events
    ) {
        this.sagaId = Objects.requireNonNull(sagaId, "sagaId");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.fromAccountId = Objects.requireNonNull(fromAccountId, "fromAccountId");
        this.toAccountId = Objects.requireNonNull(toAccountId, "toAccountId");
        this.amount = amount;
        this.currency = Objects.requireNonNull(currency, "currency");
        this.traceId = Objects.requireNonNull(traceId, "traceId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.completedAt = completedAt;
        this.fraudScore = fraudScore;
        this.authPolicy = authPolicy;
        this.fallbackMethod = fallbackMethod;
        this.state = Objects.requireNonNull(state, "state");
        this.stateHistory.addAll(List.copyOf(Objects.requireNonNull(stateHistory, "stateHistory")));
        this.events.addAll(List.copyOf(Objects.requireNonNull(events, "events")));
        if (this.stateHistory.isEmpty()) {
            throw new PaymentException("state history cannot be empty");
        }
        if (this.stateHistory.get(this.stateHistory.size() - 1) != this.state) {
            throw new PaymentException("state history must end in current state");
        }
    }

    public static PaymentSaga initiate(PaymentRequest request) {
        return new PaymentSaga(request);
    }

    public UUID sagaId() {
        return sagaId;
    }

    public UUID idempotencyKey() {
        return idempotencyKey;
    }

    public UUID userId() {
        return userId;
    }

    public UUID fromAccountId() {
        return fromAccountId;
    }

    public UUID toAccountId() {
        return toAccountId;
    }

    public long amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public String traceId() {
        return traceId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public double fraudScore() {
        return fraudScore;
    }

    public AuthPolicy authPolicy() {
        return authPolicy;
    }

    public FallbackMethod fallbackMethod() {
        return fallbackMethod;
    }

    public PaymentSagaState state() {
        return state;
    }

    public List<PaymentSagaState> stateHistory() {
        return List.copyOf(stateHistory);
    }

    public List<PaymentEvent> events() {
        return List.copyOf(events);
    }

    public PaymentSagaSnapshot snapshot() {
        return new PaymentSagaSnapshot(
                sagaId,
                idempotencyKey,
                userId,
                fromAccountId,
                toAccountId,
                amount,
                currency,
                traceId,
                createdAt,
                updatedAt,
                completedAt,
                fraudScore,
                authPolicy,
                fallbackMethod,
                state,
                stateHistory,
                events
        );
    }

    static PaymentSaga rehydrate(PaymentSagaSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new PaymentSaga(
                snapshot.sagaId(),
                snapshot.idempotencyKey(),
                snapshot.userId(),
                snapshot.fromAccountId(),
                snapshot.toAccountId(),
                snapshot.amount(),
                snapshot.currency(),
                snapshot.traceId(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.completedAt(),
                snapshot.fraudScore(),
                snapshot.authPolicy(),
                snapshot.fallbackMethod(),
                snapshot.state(),
                snapshot.stateHistory(),
                snapshot.events()
        );
    }

    public boolean canFallback() {
        return authPolicy != null && authPolicy.permitsFallback();
    }

    public boolean matchesRequest(PaymentRequest request) {
        return sagaId.equals(request.sagaId())
                && idempotencyKey.equals(request.idempotencyKey())
                && userId.equals(request.userId())
                && fromAccountId.equals(request.fromAccountId())
                && toAccountId.equals(request.toAccountId())
                && amount == request.amount()
                && currency.equals(request.currency())
                && traceId.equals(request.traceId());
    }

    public void approveFraud(FraudDecision decision) {
        ensureState(PaymentSagaState.FRAUD_CHECK_PENDING, "fraud approval");
        this.fraudScore = decision.score();
        this.authPolicy = decision.authPolicy();
        this.fallbackMethod = decision.authPolicy().permitsFallback() ? decision.authPolicy().fallbackMethod() : null;
        emit(PAYMENT_FRAUD_APPROVED, payload("score", formatScore(decision.score())));
        transition(PaymentSagaState.VOICE_VERIFICATION_PENDING, PAYMENT_VOICE_REQUESTED, payload("policy", decision.authPolicy().name()));
    }

    public void rejectFraud(FraudDecision decision) {
        ensureState(PaymentSagaState.FRAUD_CHECK_PENDING, "fraud rejection");
        this.fraudScore = decision.score();
        this.authPolicy = decision.authPolicy();
        emit(PAYMENT_FRAUD_REJECTED, payload("score", formatScore(decision.score())));
        transition(PaymentSagaState.FRAUD_REJECTED, null, null);
    }

    public void voiceApproved() {
        ensureState(PaymentSagaState.VOICE_VERIFICATION_PENDING, "voice approval");
        emit(PAYMENT_VOICE_APPROVED, payload("policy", authPolicyName()));
        transition(PaymentSagaState.FUNDS_RESERVING, null, null);
    }

    public void voiceRejected() {
        ensureState(PaymentSagaState.VOICE_VERIFICATION_PENDING, "voice rejection");
        emit(PAYMENT_VOICE_REJECTED, payload("reason", "voice verification rejected"));
        if (canFallback()) {
            transition(PaymentSagaState.VOICE_FALLBACK_PENDING, PAYMENT_VOICE_FALLBACK_REQUESTED, payload("method", fallbackMethod.name()));
            return;
        }
        transition(PaymentSagaState.VOICE_REJECTED, null, null);
    }

    public void voiceTimedOut() {
        ensureState(PaymentSagaState.VOICE_VERIFICATION_PENDING, "voice timeout");
        emit(PAYMENT_VOICE_TIMEOUT, payload("reason", "voice verification timed out"));
        if (canFallback()) {
            transition(PaymentSagaState.VOICE_FALLBACK_PENDING, PAYMENT_VOICE_FALLBACK_REQUESTED, payload("method", fallbackMethod.name()));
            return;
        }
        transition(PaymentSagaState.VOICE_VERIFICATION_TIMEOUT, null, null);
    }

    public void fallbackVerified(FallbackOutcome outcome) {
        ensureState(PaymentSagaState.VOICE_FALLBACK_PENDING, "fallback verification");
        if (!outcome.verified()) {
            throw new PaymentException("fallback must be verified to move forward");
        }
        if (outcome.method() != fallbackMethod) {
            throw new PaymentException("fallback method does not match policy");
        }
        emit(PAYMENT_VOICE_FALLBACK_VERIFIED, payload("method", outcome.method().name()));
        transition(PaymentSagaState.FUNDS_RESERVING, null, null);
    }

    public void fallbackFailed(FallbackOutcome outcome) {
        ensureState(PaymentSagaState.VOICE_FALLBACK_PENDING, "fallback failure");
        emit(PAYMENT_VOICE_FALLBACK_FAILED, payload("method", outcome.method().name()));
        transition(PaymentSagaState.VOICE_FALLBACK_FAILED, null, null);
    }

    public void fundsReserved() {
        ensureState(PaymentSagaState.FUNDS_RESERVING, "funds reservation");
        emit(PAYMENT_FUNDS_RESERVED, payload("amount", Long.toString(amount)));
        transition(PaymentSagaState.FUNDS_RESERVED, null, null);
    }

    public void fundsReservationFailed(String reason) {
        ensureState(PaymentSagaState.FUNDS_RESERVING, "funds reservation failure");
        emit(PAYMENT_FUNDS_RESERVATION_FAILED, payload("reason", reason));
        transition(PaymentSagaState.FUNDS_RESERVATION_FAILED, null, null);
    }

    public void ledgerCommitStarted() {
        ensureState(PaymentSagaState.FUNDS_RESERVED, "ledger commit start");
        emit(PAYMENT_LEDGER_COMMITTING, payload("amount", Long.toString(amount)));
        transition(PaymentSagaState.LEDGER_COMMITTING, null, null);
    }

    public void ledgerCommitSucceeded() {
        ensureState(PaymentSagaState.LEDGER_COMMITTING, "ledger commit success");
        emit(PAYMENT_LEDGER_COMMITTED, payload("amount", Long.toString(amount)));
        transition(PaymentSagaState.LEDGER_COMMITTED, null, null);
        transition(PaymentSagaState.COMPLETING, PAYMENT_COMPLETING, payload("traceId", traceId));
    }

    public void ledgerCommitFailed(String reason) {
        ensureState(PaymentSagaState.LEDGER_COMMITTING, "ledger commit failure");
        emit(PAYMENT_LEDGER_COMMIT_FAILED, payload("reason", reason));
        transition(PaymentSagaState.LEDGER_COMMIT_FAILED, null, null);
        emit(PAYMENT_COMPENSATION_IN_PROGRESS, payload("reason", reason));
        transition(PaymentSagaState.COMPENSATION_IN_PROGRESS, null, null);
    }

    public void complete() {
        ensureState(PaymentSagaState.COMPLETING, "payment completion");
        emit(PAYMENT_COMPLETED, payload("sagaId", sagaId.toString()));
        transition(PaymentSagaState.COMPLETED, null, null);
        this.completedAt = Instant.now();
    }

    public void compensationSucceeded() {
        ensureState(PaymentSagaState.COMPENSATION_IN_PROGRESS, "compensation success");
        emit(PAYMENT_COMPENSATED, payload("amount", Long.toString(amount)));
        transition(PaymentSagaState.COMPENSATED, null, null);
    }

    public void compensationFailed(String reason) {
        ensureState(PaymentSagaState.COMPENSATION_IN_PROGRESS, "compensation failure");
        emit(PAYMENT_COMPENSATION_FAILED, payload("reason", reason));
        transition(PaymentSagaState.COMPENSATION_FAILED, null, null);
    }

    public boolean isTerminal() {
        return state.isTerminal();
    }

    private void transition(PaymentSagaState nextState, String eventType, String payload) {
        this.state = Objects.requireNonNull(nextState, "nextState");
        this.stateHistory.add(nextState);
        this.updatedAt = Instant.now();
        if (eventType != null) {
            emit(eventType, payload == null ? "" : payload);
        }
    }

    private void emit(String eventType, String payload) {
        events.add(PaymentEventFactory.create(sagaId, traceId, eventType, payload));
    }

    private void ensureState(PaymentSagaState expected, String action) {
        if (state != expected) {
            throw new PaymentException(action + " requires state " + expected + " but was " + state);
        }
    }

    private String payload(String key, String value) {
        return PaymentEventFactory.payload(key, value, userId, fromAccountId, toAccountId);
    }

    private String authPolicyName() {
        return authPolicy == null ? "" : authPolicy.name();
    }

    private String formatScore(double score) {
        return String.format(java.util.Locale.ROOT, "%.3f", score);
    }

    private record ConstructionTimestamps(Instant createdAt, Instant updatedAt) {
        private ConstructionTimestamps(Instant timestamp) {
            this(timestamp, timestamp);
        }
    }
}

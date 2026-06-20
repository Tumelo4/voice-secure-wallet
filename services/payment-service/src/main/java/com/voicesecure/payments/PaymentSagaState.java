package com.voicesecure.payments;

public enum PaymentSagaState {
    INITIATED(false),
    FRAUD_CHECK_PENDING(false),
    FRAUD_REJECTED(true),
    VOICE_VERIFICATION_PENDING(false),
    VOICE_VERIFICATION_TIMEOUT(true),
    VOICE_REJECTED(true),
    VOICE_FALLBACK_PENDING(false),
    VOICE_FALLBACK_VERIFIED(false),
    VOICE_FALLBACK_FAILED(true),
    FUNDS_RESERVING(false),
    FUNDS_RESERVATION_FAILED(true),
    FUNDS_RESERVED(false),
    LEDGER_COMMITTING(false),
    LEDGER_COMMIT_FAILED(false),
    LEDGER_COMMITTED(false),
    COMPLETING(false),
    COMPLETED(true),
    COMPENSATION_IN_PROGRESS(false),
    COMPENSATED(true),
    COMPENSATION_FAILED(true);

    private final boolean terminal;

    PaymentSagaState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}

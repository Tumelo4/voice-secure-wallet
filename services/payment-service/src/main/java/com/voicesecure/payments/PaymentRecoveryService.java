package com.voicesecure.payments;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PaymentRecoveryService {
    private final PaymentSagaRepository repository;
    private final Clock clock;
    private final Duration stuckAfter;

    public PaymentRecoveryService(PaymentSagaRepository repository, Clock clock, Duration stuckAfter) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.stuckAfter = Objects.requireNonNull(stuckAfter, "stuckAfter");
        if (stuckAfter.isZero() || stuckAfter.isNegative()) throw new PaymentException("stuck threshold must be positive");
    }

    public List<StuckPayment> scan() {
        Instant now = clock.instant();
        List<StuckPayment> found = new ArrayList<>();
        for (PaymentSaga saga : repository.findNonTerminalUpdatedBefore(now.minus(stuckAfter))) {
            String action = recoveryAction(saga);
            if (saga.state() == PaymentSagaState.UNKNOWN_EXTERNAL_STATUS) {
                saga.beginReconciliation();
                repository.save(saga);
            }
            found.add(new StuckPayment(saga.sagaId(), saga.state(), saga.updatedAt(), Duration.between(saga.updatedAt(), now), action));
        }
        return List.copyOf(found);
    }

    private static String recoveryAction(PaymentSaga saga) {
        return switch (saga.state()) {
            case UNKNOWN_EXTERNAL_STATUS -> "RECONCILIATION_STARTED";
            case RECONCILIATION_REQUIRED, MANUAL_REVIEW -> "OPERATOR_ALERT";
            default -> "RETRY_OR_EXPIRE";
        };
    }
}

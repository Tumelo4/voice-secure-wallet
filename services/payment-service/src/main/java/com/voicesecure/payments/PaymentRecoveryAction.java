package com.voicesecure.payments;

@FunctionalInterface
public interface PaymentRecoveryAction {
    PaymentSaga recover(PaymentSaga saga);

    PaymentRecoveryAction NOOP = saga -> saga;
}

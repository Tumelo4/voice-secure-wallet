package com.voicesecure.api;

import com.voicesecure.payments.PaymentSaga;

@FunctionalInterface
public interface PaymentSettlementHandler {
    PaymentSettlementHandler NOOP = saga -> saga;

    PaymentSaga settle(PaymentSaga saga);
}

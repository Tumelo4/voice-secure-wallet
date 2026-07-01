package com.voicesecure.api;

import com.voicesecure.payments.FraudDecision;
import com.voicesecure.payments.PaymentRequest;

@FunctionalInterface
public interface FraudDecisionProvider {
    FraudDecision assess(PaymentRequest request);
}

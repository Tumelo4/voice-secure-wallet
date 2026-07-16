package com.voicesecure.payments;

import java.util.List;

@FunctionalInterface
public interface PaymentRecoveryCoordinator {
    List<StuckPayment> runOnce();
}

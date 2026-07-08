package com.voicesecure.acceptance;

public record PaymentOutcome(
        String paymentState,
        long sourceBalance,
        long destinationBalance,
        long reconciliationTotal,
        int ledgerEntryCount,
        int walletProjectionCount,
        int notificationCount
) {
}

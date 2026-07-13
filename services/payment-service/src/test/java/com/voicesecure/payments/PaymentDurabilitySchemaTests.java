package com.voicesecure.payments;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PaymentDurabilitySchemaTests {
    public static void main(String[] args) throws Exception {
        String version = Files.readString(Path.of("services/payment-service/src/main/resources/db/migration/V002__payment_saga_version.sql"));
        String states = Files.readString(Path.of("services/payment-service/src/main/resources/db/migration/V003__payment_reconciliation_states.sql"));
        String references = Files.readString(Path.of("services/payment-service/src/main/resources/db/migration/V004__customer_payment_references.sql"));
        require(version, "version BIGINT NOT NULL", "persisted saga version");
        require(version, "version >= 0", "version constraint");
        require(states, "UNKNOWN_EXTERNAL_STATUS", "unknown outcome state");
        require(states, "RECONCILIATION_REQUIRED", "reconciliation state");
        require(states, "MANUAL_REVIEW", "manual review state");
        require(references, "payment_reference VARCHAR(32) PRIMARY KEY", "opaque public reference");
        require(references, "saga_id UUID NOT NULL UNIQUE", "one stable reference per saga");
        require(references, "customer_id UUID NOT NULL", "payment ownership");
        System.out.println("Payment durability schema tests passed: 8");
    }

    private static void require(String actual, String expected, String message) {
        if (!actual.contains(expected)) throw new AssertionError(message + " missing " + expected);
    }
}

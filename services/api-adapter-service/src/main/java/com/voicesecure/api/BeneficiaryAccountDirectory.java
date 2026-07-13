package com.voicesecure.api;

import java.util.Objects;
import java.util.UUID;

@FunctionalInterface
public interface BeneficiaryAccountDirectory {
    ResolvedBeneficiaryAccount resolve(String bankCode, String accountNumber);

    record ResolvedBeneficiaryAccount(UUID destinationAccountId, String currency, boolean verified) {
        public ResolvedBeneficiaryAccount {
            Objects.requireNonNull(destinationAccountId, "destinationAccountId");
            currency = Objects.requireNonNull(currency, "currency").trim().toUpperCase(java.util.Locale.ROOT);
        }
    }
}

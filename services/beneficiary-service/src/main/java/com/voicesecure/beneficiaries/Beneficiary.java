package com.voicesecure.beneficiaries;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record Beneficiary(
        UUID beneficiaryId,
        UUID customerId,
        UUID destinationAccountId,
        String displayName,
        String maskedAccountNumber,
        String currency,
        BeneficiaryStatus status,
        Instant availableAt,
        Instant createdAt
) {
    public Beneficiary {
        Objects.requireNonNull(beneficiaryId, "beneficiaryId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(destinationAccountId, "destinationAccountId");
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        maskedAccountNumber = Objects.requireNonNull(maskedAccountNumber, "maskedAccountNumber").trim();
        currency = Objects.requireNonNull(currency, "currency").trim().toUpperCase(Locale.ROOT);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(availableAt, "availableAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (displayName.isEmpty() || displayName.length() > 80) throw new BeneficiaryException("invalid beneficiary name");
        if (!currency.matches("[A-Z]{3}")) throw new BeneficiaryException("invalid beneficiary currency");
    }

    public boolean availableForPayment(Instant now) {
        return status == BeneficiaryStatus.ACTIVE
                || (status == BeneficiaryStatus.COOLING_OFF && !now.isBefore(availableAt));
    }
}

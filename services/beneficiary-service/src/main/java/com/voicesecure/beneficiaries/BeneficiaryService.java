package com.voicesecure.beneficiaries;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class BeneficiaryService {
    private final BeneficiaryRepository repository;
    private final BeneficiaryRiskPolicy riskPolicy;
    private final Clock clock;

    public BeneficiaryService(BeneficiaryRepository repository, BeneficiaryRiskPolicy riskPolicy, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.riskPolicy = Objects.requireNonNull(riskPolicy, "riskPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized Beneficiary create(UUID customerId, UUID destinationAccountId, String name, String accountNumber, String currency) {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(destinationAccountId, "destinationAccountId");
        if (repository.findByCustomerAndDestination(customerId, destinationAccountId).isPresent()) {
            throw new BeneficiaryException("beneficiary already exists");
        }
        Instant now = clock.instant();
        Duration coolingOff = riskPolicy.coolingOffPeriod(customerId, destinationAccountId);
        if (coolingOff.isNegative()) throw new BeneficiaryException("invalid cooling-off period");
        Beneficiary beneficiary = new Beneficiary(
                UUID.randomUUID(), customerId, destinationAccountId, name, mask(accountNumber), currency,
                coolingOff.isZero() ? BeneficiaryStatus.ACTIVE : BeneficiaryStatus.COOLING_OFF,
                now.plus(coolingOff), now
        );
        repository.save(beneficiary);
        return beneficiary;
    }

    public synchronized Beneficiary registerVerified(
            UUID beneficiaryId, UUID customerId, UUID destinationAccountId,
            String name, String accountNumber, String currency
    ) {
        Objects.requireNonNull(beneficiaryId, "beneficiaryId");
        if (repository.find(beneficiaryId).isPresent()
                || repository.findByCustomerAndDestination(customerId, destinationAccountId).isPresent()) {
            throw new BeneficiaryException("beneficiary already exists");
        }
        Instant now = clock.instant();
        Beneficiary beneficiary = new Beneficiary(
                beneficiaryId, customerId, destinationAccountId, name, mask(accountNumber), currency,
                BeneficiaryStatus.ACTIVE, now, now
        );
        repository.save(beneficiary);
        return beneficiary;
    }

    public List<Beneficiary> forCustomer(UUID customerId) {
        return repository.findByCustomer(Objects.requireNonNull(customerId, "customerId"));
    }

    public Beneficiary requireAvailable(UUID customerId, UUID beneficiaryId) {
        Beneficiary beneficiary = repository.find(beneficiaryId)
                .filter(value -> value.customerId().equals(customerId))
                .orElseThrow(() -> new BeneficiaryException("beneficiary unavailable"));
        if (!beneficiary.availableForPayment(clock.instant())) throw new BeneficiaryException("beneficiary cooling off");
        return beneficiary;
    }

    private static String mask(String accountNumber) {
        String value = Objects.requireNonNull(accountNumber, "accountNumber").replaceAll("[^A-Za-z0-9]", "");
        if (value.length() < 4) throw new BeneficiaryException("invalid account number");
        return "•••• " + value.substring(value.length() - 4).toUpperCase(java.util.Locale.ROOT);
    }
}

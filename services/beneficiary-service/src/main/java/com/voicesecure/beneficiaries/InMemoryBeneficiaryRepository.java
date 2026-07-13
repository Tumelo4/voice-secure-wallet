package com.voicesecure.beneficiaries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryBeneficiaryRepository implements BeneficiaryRepository {
    private final Map<UUID, Beneficiary> beneficiaries = new HashMap<>();

    public synchronized void save(Beneficiary beneficiary) {
        beneficiaries.put(beneficiary.beneficiaryId(), beneficiary);
    }

    public synchronized Optional<Beneficiary> find(UUID beneficiaryId) {
        return Optional.ofNullable(beneficiaries.get(beneficiaryId));
    }

    public synchronized Optional<Beneficiary> findByCustomerAndDestination(UUID customerId, UUID destinationAccountId) {
        return beneficiaries.values().stream()
                .filter(value -> value.customerId().equals(customerId) && value.destinationAccountId().equals(destinationAccountId))
                .findFirst();
    }

    public synchronized List<Beneficiary> findByCustomer(UUID customerId) {
        List<Beneficiary> result = new ArrayList<>();
        beneficiaries.values().stream().filter(value -> value.customerId().equals(customerId)).forEach(result::add);
        result.sort(Comparator.comparing(Beneficiary::createdAt).thenComparing(Beneficiary::beneficiaryId));
        return List.copyOf(result);
    }
}

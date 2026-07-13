package com.voicesecure.beneficiaries;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BeneficiaryRepository {
    void save(Beneficiary beneficiary);
    Optional<Beneficiary> find(UUID beneficiaryId);
    Optional<Beneficiary> findByCustomerAndDestination(UUID customerId, UUID destinationAccountId);
    List<Beneficiary> findByCustomer(UUID customerId);
}

package com.voicesecure.beneficiaries;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public final class BeneficiaryServiceTests {
    private static final UUID CUSTOMER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DESTINATION = UUID.fromString("22222222-2222-4222-8222-222222222222");

    public static void main(String[] args) {
        newBeneficiaryIsMaskedAndCooledOff();
        duplicateBeneficiaryIsRejected();
        crossCustomerLookupIsRejected();
        System.out.println("Beneficiary service tests passed: 3");
    }

    private static void newBeneficiaryIsMaskedAndCooledOff() {
        BeneficiaryService service = service(Duration.ofHours(24));
        Beneficiary beneficiary = service.create(CUSTOMER, DESTINATION, "Maya Nkosi", "1234 5678 9012", "zar");
        assertEquals("•••• 9012", beneficiary.maskedAccountNumber(), "masked number");
        assertEquals(BeneficiaryStatus.COOLING_OFF, beneficiary.status(), "status");
        expectFailure(() -> service.requireAvailable(CUSTOMER, beneficiary.beneficiaryId()), "cooling off");
    }

    private static void duplicateBeneficiaryIsRejected() {
        BeneficiaryService service = service(Duration.ZERO);
        service.create(CUSTOMER, DESTINATION, "Maya", "12349012", "ZAR");
        expectFailure(() -> service.create(CUSTOMER, DESTINATION, "Maya again", "12349012", "ZAR"), "already exists");
    }

    private static void crossCustomerLookupIsRejected() {
        BeneficiaryService service = service(Duration.ZERO);
        Beneficiary beneficiary = service.create(CUSTOMER, DESTINATION, "Maya", "12349012", "ZAR");
        expectFailure(() -> service.requireAvailable(UUID.randomUUID(), beneficiary.beneficiaryId()), "unavailable");
    }

    private static BeneficiaryService service(Duration coolingOff) {
        return new BeneficiaryService(
                new InMemoryBeneficiaryRepository(),
                (customer, destination) -> coolingOff,
                Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    private static void expectFailure(Runnable action, String message) {
        try { action.run(); } catch (BeneficiaryException exception) {
            if (exception.getMessage().contains(message)) return;
            throw new AssertionError("wrong error: " + exception.getMessage());
        }
        throw new AssertionError("expected failure");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + " but got " + actual);
    }
}

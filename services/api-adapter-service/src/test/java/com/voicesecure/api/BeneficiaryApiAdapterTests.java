package com.voicesecure.api;

import com.voicesecure.beneficiaries.BeneficiaryService;
import com.voicesecure.beneficiaries.InMemoryBeneficiaryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

public final class BeneficiaryApiAdapterTests {
    private static final UUID CUSTOMER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DESTINATION = UUID.fromString("22222222-2222-4222-8222-222222222222");

    public static void main(String[] args) {
        BeneficiaryService service = new BeneficiaryService(
                new InMemoryBeneficiaryRepository(), (customer, destination) -> Duration.ofHours(24),
                Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), ZoneOffset.UTC));
        BeneficiaryApiAdapter adapter = new BeneficiaryApiAdapter(
                service, (bank, account) -> new BeneficiaryAccountDirectory.ResolvedBeneficiaryAccount(DESTINATION, "ZAR", true));
        ApiResponse created = adapter.handle(new ApiRequest("POST", "/v1/me/beneficiaries", headers(),
                "{\"displayName\":\"Maya Nkosi\",\"bankCode\":\"NED\",\"accountNumber\":\"123456789012\"}"));
        assertEquals(201, created.status(), "created status");
        assertContains(created.body(), "•••• 9012", "masked number");
        assertContains(created.body(), "COOLING_OFF", "risk cooling-off");
        assertNotContains(created.body(), DESTINATION.toString(), "destination account must stay internal");

        ApiResponse listed = adapter.handle(new ApiRequest("GET", "/v1/me/beneficiaries", headers(), ""));
        assertEquals(200, listed.status(), "list status");
        assertContains(listed.body(), "Maya Nkosi", "owned beneficiary");

        ApiResponse duplicate = adapter.handle(new ApiRequest("POST", "/v1/me/beneficiaries", headers(),
                "{\"displayName\":\"Maya again\",\"bankCode\":\"NED\",\"accountNumber\":\"123456789012\"}"));
        assertEquals(409, duplicate.status(), "duplicate status");
        System.out.println("Beneficiary API adapter tests passed: 3");
    }

    private static Map<String, String> headers() {
        return Map.of(ApiSecurityContext.AUTHENTICATED_PRINCIPAL_HEADER, CUSTOMER.toString());
    }
    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + " but got " + actual);
    }
    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) throw new AssertionError(message + ": missing " + expected);
    }
    private static void assertNotContains(String actual, String expected, String message) {
        if (actual.contains(expected)) throw new AssertionError(message + ": found " + expected);
    }
}

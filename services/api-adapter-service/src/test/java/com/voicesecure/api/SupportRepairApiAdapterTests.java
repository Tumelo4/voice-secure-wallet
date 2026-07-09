package com.voicesecure.api;

import com.voicesecure.ledger.InMemoryLedgerRepository;
import com.voicesecure.ledger.LedgerService;
import com.voicesecure.support.InMemorySupportRepository;
import com.voicesecure.support.SupportService;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SupportRepairApiAdapterTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("support repair POST applies a balanced repair and records audit evidence", SupportRepairApiAdapterTests::supportRepairPostAppliesRepair),
                new TestCase("support repair POST requires a meaningful justification", SupportRepairApiAdapterTests::supportRepairPostRequiresJustification)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Support repair API adapter tests passed: " + tests.length);
    }

    private static void supportRepairPostAppliesRepair() {
        Fixture fixture = fixture();
        UUID repairId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID sagaId = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        UUID idempotencyKey = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

        ApiResponse response = fixture.router.handle(new ApiRequest(
                "POST",
                "/support/repairs",
                Map.of("Idempotency-Key", idempotencyKey.toString(), "X-Trace-Id", "trace-support-api-1"),
                repairBody(repairId, sagaId, fixture.sourceAccountId, fixture.destinationAccountId, 50, "COMPENSATION_FAILED drill corrective entry", "sre@example.com")
        ));

        assertEquals(200, response.status(), "repair status");
        assertContains(response.body(), "\"repairId\":\"" + repairId + "\"", "repair id");
        assertContains(response.body(), "\"entryCount\":2", "entry count");
        assertContains(response.body(), "\"status\":\"APPLIED\"", "repair status");
        assertContains(response.body(), "\"traceId\":\"trace-support-api-1\"", "trace id");
        assertEquals(1, fixture.supportRepository.cases().size(), "support case count");
        assertEquals(1L, fixture.supportRepository.auditLog().stream().filter(entry -> entry.action().equals("support.repair_requested")).count(), "requested audit entry");
        assertEquals(1L, fixture.supportRepository.auditLog().stream().filter(entry -> entry.action().equals("support.repair_linked")).count(), "linked audit entry");
        assertEquals(2, fixture.ledgerRepository.entries().size(), "ledger entries");
        assertEquals(1, fixture.ledgerRepository.repairAudit().size(), "repair audit");
    }

    private static void supportRepairPostRequiresJustification() {
        Fixture fixture = fixture();
        UUID repairId = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");
        UUID sagaId = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
        UUID idempotencyKey = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");

        ApiResponse response = fixture.router.handle(new ApiRequest(
                "POST",
                "/support/repairs",
                Map.of("Idempotency-Key", idempotencyKey.toString(), "X-Trace-Id", "trace-support-api-2"),
                repairBody(repairId, sagaId, fixture.sourceAccountId, fixture.destinationAccountId, 50, "too short", "sre@example.com")
        ));

        assertEquals(400, response.status(), "validation status");
        assertContains(response.body(), "\"code\":\"VALIDATION_FAILED\"", "validation code");
        assertContains(response.body(), "repair justification must be at least 12 characters", "justification error");
        assertEquals(0, fixture.supportRepository.cases().size(), "support case should not be saved");
        assertEquals(0, fixture.ledgerRepository.entries().size(), "ledger should not be touched");
    }

    private static Fixture fixture() {
        InMemoryLedgerRepository ledgerRepository = new InMemoryLedgerRepository();
        LedgerService ledgerService = new LedgerService(ledgerRepository);
        UUID sourceAccountId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID destinationAccountId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        ledgerService.createAccount(sourceAccountId, "ZAR", 1_000);
        ledgerService.createAccount(destinationAccountId, "ZAR", 0);

        InMemorySupportRepository supportRepository = new InMemorySupportRepository();
        SupportService supportService = new SupportService(supportRepository, ledgerService);
        ApiRouter router = new ApiRouter(List.of(new SupportRepairApiAdapter(supportService)));

        return new Fixture(router, supportRepository, ledgerRepository, sourceAccountId, destinationAccountId);
    }

    private static String repairBody(
            UUID repairId,
            UUID sagaId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            long amount,
            String justification,
            String requestedBy
    ) {
        return "{"
                + "\"repairId\":\"" + repairId + "\","
                + "\"sagaId\":\"" + sagaId + "\","
                + "\"sourceAccountId\":\"" + sourceAccountId + "\","
                + "\"destinationAccountId\":\"" + destinationAccountId + "\","
                + "\"amount\":" + amount + ","
                + "\"currency\":\"ZAR\","
                + "\"justification\":\"" + justification + "\","
                + "\"requestedBy\":\"" + requestedBy + "\""
                + "}";
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertContains(String actual, String expected, String message) {
        if (!actual.contains(expected)) {
            throw new AssertionError(message + ": expected to find " + expected + " in " + actual);
        }
    }

    private record Fixture(
            ApiRouter router,
            InMemorySupportRepository supportRepository,
            InMemoryLedgerRepository ledgerRepository,
            UUID sourceAccountId,
            UUID destinationAccountId
    ) {
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}

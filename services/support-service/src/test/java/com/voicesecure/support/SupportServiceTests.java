package com.voicesecure.support;

import com.voicesecure.ledger.InMemoryLedgerRepository;
import com.voicesecure.ledger.LedgerBatch;
import com.voicesecure.ledger.LedgerService;
import com.voicesecure.ledger.Posting;
import com.voicesecure.ledger.RepairRequest;
import java.util.List;
import java.util.UUID;

public final class SupportServiceTests {
    public static void main(String[] args) {
        TestCase[] tests = {
                new TestCase("searches the projected ledger replica", SupportServiceTests::searchesProjectedLedgerReplica),
                new TestCase("freeze and dispute actions are audited", SupportServiceTests::freezeAndDisputeActionsAreAudited),
                new TestCase("repair requests link support to the ledger repair path", SupportServiceTests::repairRequestsLinkToLedgerRepair)
        };

        for (TestCase test : tests) {
            test.run();
            System.out.println("PASS " + test.name);
        }
        System.out.println("Support service tests passed: " + tests.length);
    }

    private static void searchesProjectedLedgerReplica() {
        Fixture fixture = fixture();
        LedgerBatch batch = fixture.ledgerService.transfer(
                UUID.randomUUID(),
                UUID.randomUUID(),
                fixture.source,
                fixture.destination,
                250,
                "ZAR"
        );
        fixture.service.ingestLedgerBatch(batch);

        List<SupportTransactionView> sourceViews = fixture.service.searchTransactions(fixture.source, "ZAR");
        List<SupportTransactionView> destinationViews = fixture.service.searchTransactions(fixture.destination, "ZAR");

        assertEquals(1, sourceViews.size(), "source should have one projected transaction");
        assertEquals(1, destinationViews.size(), "destination should have one projected transaction");
        assertEquals(fixture.source, sourceViews.get(0).accountId(), "source account id");
        assertEquals(fixture.destination, destinationViews.get(0).accountId(), "destination account id");
    }

    private static void freezeAndDisputeActionsAreAudited() {
        Fixture fixture = fixture();
        SupportCase freeze = fixture.service.freezeAccount(fixture.source, "suspicious activity", "support-agent");
        SupportCase dispute = fixture.service.openDispute(fixture.destination, UUID.randomUUID(), "chargeback request", "support-agent");

        assertEquals(SupportCaseStatus.FROZEN, freeze.status(), "freeze status");
        assertEquals(SupportCaseStatus.DISPUTED, dispute.status(), "dispute status");
        assertEquals(2, fixture.repository.auditLog().size(), "audit entries");
        assertEquals(2, fixture.repository.cases().size(), "stored support cases");
    }

    private static void repairRequestsLinkToLedgerRepair() {
        Fixture fixture = fixture();
        LedgerBatch transfer = fixture.ledgerService.transfer(
                UUID.randomUUID(),
                UUID.randomUUID(),
                fixture.source,
                fixture.destination,
                400,
                "ZAR"
        );
        fixture.service.ingestLedgerBatch(transfer);

        RepairRequest repairRequest = new RepairRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ZAR",
                List.of(
                        Posting.repairDebit(fixture.destination, 50),
                        Posting.repairCredit(fixture.source, 50)
                ),
                "COMPENSATION_FAILED drill corrective entry",
                "sre@example.com"
        );

        LedgerBatch repaired = fixture.service.requestRepair(repairRequest);

        assertEquals(2, repaired.entries().size(), "repair should produce balanced entries");
        assertEquals(1L, fixture.repository.cases().stream().filter(caseRecord -> caseRecord.type() == SupportCaseType.REPAIR_ESCALATION).count(), "repair case should be stored");
        assertEquals(1L, fixture.repository.auditLog().stream().filter(entry -> entry.action().equals("support.repair_linked")).count(), "repair audit should be stored");
        assertEquals(2, fixture.service.searchTransactions(fixture.source, "ZAR").size(), "source replica should include repair entry");
        assertEquals(2, fixture.service.searchTransactions(fixture.destination, "ZAR").size(), "destination replica should include repair entry");
    }

    private static Fixture fixture() {
        InMemoryLedgerRepository ledgerRepository = new InMemoryLedgerRepository();
        LedgerService ledgerService = new LedgerService(ledgerRepository);
        InMemorySupportRepository repository = new InMemorySupportRepository();
        SupportService service = new SupportService(repository, ledgerService);
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        ledgerService.createAccount(source, "ZAR", 1_000);
        ledgerService.createAccount(destination, "ZAR", 0);
        return new Fixture(service, repository, ledgerService, source, destination);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private record Fixture(
            SupportService service,
            InMemorySupportRepository repository,
            LedgerService ledgerService,
            UUID source,
            UUID destination
    ) {
    }

    private record TestCase(String name, Runnable runnable) {
        void run() {
            runnable.run();
        }
    }
}

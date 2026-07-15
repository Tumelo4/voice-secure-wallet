package com.voicesecure.support;

import com.voicesecure.ledger.LedgerBatch;
import com.voicesecure.ledger.application.LedgerService;
import com.voicesecure.ledger.RepairRequest;
import java.util.Objects;

public final class LedgerServiceRepairPort implements LedgerRepairPort {
    private final LedgerService ledgerService;

    public LedgerServiceRepairPort(LedgerService ledgerService) {
        this.ledgerService = Objects.requireNonNull(ledgerService, "ledgerService");
    }

    @Override
    public LedgerBatch repair(RepairRequest repairRequest) {
        return ledgerService.repair(repairRequest);
    }
}

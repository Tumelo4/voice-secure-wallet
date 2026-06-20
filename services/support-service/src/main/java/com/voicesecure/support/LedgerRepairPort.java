package com.voicesecure.support;

import com.voicesecure.ledger.LedgerBatch;
import com.voicesecure.ledger.RepairRequest;

public interface LedgerRepairPort {
    LedgerBatch repair(RepairRequest repairRequest);
}

package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReconciliationResult {
    private UUID transactionId;
    private boolean reconciled;
    private ExternalTransactionRecord matchedExternalRecord;
    private List<ReconciliationDiscrepancy> discrepancies;
    
    public String getDiscrepancySummary() {
        if (discrepancies == null || discrepancies.isEmpty()) {
            return "No discrepancies found";
        }
        return "Found " + discrepancies.size() + " discrepancies";
    }
}
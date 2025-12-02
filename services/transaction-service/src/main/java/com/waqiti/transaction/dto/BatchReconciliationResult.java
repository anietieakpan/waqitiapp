package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BatchReconciliationResult {
    private String batchId;
    private int totalTransactions;
    private int reconciledTransactions;
    private int failedReconciliations;
    private int discrepancyCount;
    private List<UUID> reconciledTransactionIds;
    private List<UUID> failedReconciliationIds;
    private List<ReconciliationDiscrepancy> discrepancies;
    private ReconciliationReport reconciliationReport;
    private boolean reconciliationSuccessful;
    private LocalDateTime completedAt;
}
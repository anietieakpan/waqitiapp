package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReconciliationReport {
    private String batchId;
    private int totalTransactions;
    private int totalExternalRecords;
    private int totalDiscrepancies;
    private List<ReconciliationDiscrepancy> discrepancies;
    private ReconciliationAnalysis analysis;
    private LocalDateTime generatedAt;
}
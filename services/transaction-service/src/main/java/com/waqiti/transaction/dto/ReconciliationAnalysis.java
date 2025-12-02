package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ReconciliationAnalysis {
    private int totalInternalTransactions;
    private int totalExternalRecords;
    private BigDecimal totalInternalAmount;
    private BigDecimal totalExternalAmount;
    private BigDecimal amountDiscrepancy;
    private double matchPercentage;
    private LocalDateTime analysisCompletedAt;
}
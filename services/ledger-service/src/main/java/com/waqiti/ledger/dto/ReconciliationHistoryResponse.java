package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reconciliation History Response DTO
 * 
 * Contains historical reconciliation data for analysis and reporting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationHistoryResponse {
    
    private UUID bankAccountId;
    private LocalDate startDate;
    private LocalDate endDate;
    
    // Historical reconciliations
    private List<HistoricalReconciliation> reconciliations;
    private Integer totalReconciliations;
    
    // Trend analysis
    private BigDecimal averageVariance;
    private BigDecimal maxVariance;
    private BigDecimal minVariance;
    private LocalDate lastReconciliationDate;
    
    // Performance metrics
    private BigDecimal averageMatchingRate;
    private Integer averageOutstandingItems;
    private String overallTrend; // IMPROVING, STABLE, DETERIORATING
    
    // Generation info
    private LocalDateTime generatedAt;
    private String generatedBy;
}
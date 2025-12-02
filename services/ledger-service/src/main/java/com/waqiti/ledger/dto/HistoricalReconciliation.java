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
 * DTO for historical reconciliation records
 * Contains historical data and trends for inter-company reconciliation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalReconciliation {
    
    private UUID historicalReconciliationId;
    private UUID reconciliationId;
    private LocalDate reconciliationDate;
    private LocalDateTime completedAt;
    private UUID sourceEntityId;
    private String sourceEntityName;
    private UUID targetEntityId;
    private String targetEntityName;
    private String reconciliationStatus; // COMPLETED, PARTIAL, FAILED
    private int totalTransactionCount;
    private BigDecimal totalTransactionAmount;
    private String currency;
    private int matchedTransactionCount;
    private BigDecimal matchedAmount;
    private BigDecimal matchingPercentage;
    private int unmatchedTransactionCount;
    private BigDecimal unmatchedAmount;
    private int discrepancyCount;
    private BigDecimal totalVarianceAmount;
    private int resolvedDiscrepancies;
    private int outstandingDiscrepancies;
    private BigDecimal processingTime; // in minutes
    private String performedBy;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reconciliationMethod; // AUTOMATIC, MANUAL, HYBRID
    private List<String> keyFindings;
    private String overallAssessment;
    private boolean exceedsThresholds;
    private String riskRating; // LOW, MEDIUM, HIGH, CRITICAL
    private String archivalStatus; // ACTIVE, ARCHIVED
    private LocalDateTime archivedAt;
    private String notes;
}
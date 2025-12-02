package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for inter-company reconciliation summary
 * Provides high-level summary of reconciliation process and results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterCompanyReconciliationSummary {
    
    private UUID summaryId;
    private UUID reconciliationId;
    private LocalDate reconciliationDate;
    private LocalDateTime summaryGeneratedAt;
    private String reconciliationStatus; // COMPLETED, IN_PROGRESS, FAILED, PARTIAL
    private List<EntityPair> entitiesReconciled;
    private int totalEntityPairs;
    private String currency;
    private BigDecimal totalReconciledAmount;
    private int totalTransactionsProcessed;
    private int totalMatchedTransactions;
    private int totalUnmatchedTransactions;
    private BigDecimal totalVarianceAmount;
    private BigDecimal matchingAccuracy;
    private int totalDiscrepancies;
    private int highPriorityDiscrepancies;
    private int mediumPriorityDiscrepancies;
    private int lowPriorityDiscrepancies;
    private int resolvedDiscrepancies;
    private int openDiscrepancies;
    private BigDecimal materialVarianceAmount;
    private boolean exceedsMaterialityThreshold;
    private Map<String, Integer> discrepanciesByType;
    private Map<String, BigDecimal> varianceByEntityPair;
    private List<String> keyFindings;
    private List<String> actionItems;
    private String overallAssessment;
    private boolean requiresFollowUp;
    private LocalDate nextReconciliationDate;
    private String performedBy;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String notes;
}
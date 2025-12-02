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
 * DTO for comprehensive inter-company analysis results
 * Contains detailed analysis of inter-company reconciliation process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterCompanyAnalysis {
    
    private UUID analysisId;
    private UUID reconciliationId;
    private LocalDate reconciliationDate;
    private LocalDateTime analysisDate;
    private String analysisType; // FULL, DELTA, VARIANCE_ONLY
    private String analysisStatus; // COMPLETED, IN_PROGRESS, FAILED
    private List<EntityPair> entityPairs;
    private int totalTransactionCount;
    private BigDecimal totalTransactionAmount;
    private String currency;
    private int matchedTransactionCount;
    private BigDecimal matchedAmount;
    private int unmatchedTransactionCount;
    private BigDecimal unmatchedAmount;
    private BigDecimal matchingPercentage;
    private BigDecimal varianceAmount;
    private BigDecimal variancePercentage;
    private List<InterCompanyMatching> matches;
    private InterCompanyDiscrepancies discrepancies;
    private Map<String, BigDecimal> varianceByAccount;
    private Map<String, Integer> transactionCountByType;
    private Map<String, BigDecimal> amountByTransactionType;
    private List<String> criticalFindings;
    private List<String> recommendations;
    private BigDecimal materialityThreshold;
    private boolean exceedsMaterialityThreshold;
    private String riskAssessment; // LOW, MEDIUM, HIGH, CRITICAL
    private boolean requiresManagementReview;
    private String performedBy;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String conclusions;
    private String nextSteps;
    private LocalDate nextAnalysisDate;
}
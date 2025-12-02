package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for detailed breakdown of variances in reconciliation
 * Provides comprehensive analysis of variance types and causes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VarianceBreakdown {
    
    private UUID breakdownId;
    private UUID reconciliationId;
    private LocalDate analysisDate;
    private UUID sourceEntityId;
    private String sourceEntityName;
    private UUID targetEntityId;
    private String targetEntityName;
    private BigDecimal totalVarianceAmount;
    private String currency;
    private Map<String, BigDecimal> varianceByType; // TIMING, AMOUNT, CLASSIFICATION, MISSING
    private Map<String, BigDecimal> varianceByAccount;
    private Map<String, Integer> varianceCountByType;
    private Map<String, BigDecimal> varianceByTransactionType;
    private Map<String, BigDecimal> varianceByPeriod;
    private List<VarianceItem> significantVariances;
    private BigDecimal materialityThreshold;
    private int materialVarianceCount;
    private BigDecimal materialVarianceAmount;
    private BigDecimal immaterialVarianceAmount;
    private String primaryVarianceCause;
    private List<String> topVarianceReasons;
    private BigDecimal averageVarianceAmount;
    private BigDecimal medianVarianceAmount;
    private BigDecimal maxVarianceAmount;
    private BigDecimal minVarianceAmount;
    private String trendAnalysis;
    private boolean improvementOverPreviousPeriod;
    private String recommendedActions;
    private String analysisNotes;
    private String performedBy;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class VarianceItem {
    private UUID itemId;
    private String accountCode;
    private String accountName;
    private BigDecimal varianceAmount;
    private String varianceType;
    private String description;
    private String rootCause;
    private boolean material;
    private String priority;
}
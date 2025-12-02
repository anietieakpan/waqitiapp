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
 * DTO for discrepancy analysis between inter-company entities
 * Contains collections of discrepancies and summary information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterCompanyDiscrepancies {
    
    private UUID analysisId;
    private UUID sourceEntityId;
    private String sourceEntityName;
    private UUID targetEntityId;
    private String targetEntityName;
    private LocalDate reconciliationDate;
    private LocalDate analysisDate;
    private String analysisStatus;
    private List<InterCompanyDiscrepancy> discrepancies;
    private int totalDiscrepancyCount;
    private BigDecimal totalDiscrepancyAmount;
    private String currency;
    private Map<String, Integer> discrepancyCountByType;
    private Map<String, BigDecimal> discrepancyAmountByType;
    private Map<String, Integer> discrepancyCountByPriority;
    private BigDecimal materialityThreshold;
    private List<String> accountsWithDiscrepancies;
    private boolean requiresReview;
    private boolean hasHighPriorityItems;
    private String performedBy;
    private String reviewedBy;
    private String notes;
    private LocalDate nextReviewDate;
}
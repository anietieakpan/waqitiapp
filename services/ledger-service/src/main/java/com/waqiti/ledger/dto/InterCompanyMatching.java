package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for matching results between inter-company transactions
 * Contains the results of automatic and manual matching processes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterCompanyMatching {
    
    private UUID matchingId;
    private UUID sourceEntityId;
    private UUID targetEntityId;
    private String matchingType; // AUTOMATIC, MANUAL, SUGGESTED
    private String matchingStatus; // MATCHED, PARTIALLY_MATCHED, UNMATCHED, DISPUTED
    private LocalDateTime matchingDate;
    private String matchedBy;
    private BigDecimal matchedAmount;
    private String currency;
    private BigDecimal variance;
    private String varianceReason;
    private List<InterCompanyTransaction> sourceTransactions;
    private List<InterCompanyTransaction> targetTransactions;
    private BigDecimal confidenceScore;
    private String matchingCriteria;
    private LocalDateTime reconciledAt;
    private String reconciledBy;
    private String notes;
    private boolean requiresApproval;
    private LocalDateTime approvedAt;
    private String approvedBy;
    private String approvalNotes;
    private LocalDateTime createdAt;
    private String createdBy;
}
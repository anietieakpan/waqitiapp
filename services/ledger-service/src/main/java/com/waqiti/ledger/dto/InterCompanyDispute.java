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
 * DTO for inter-company dispute details
 * Represents a dispute between two entities in inter-company reconciliation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterCompanyDispute {
    
    private UUID disputeId;
    private String disputeNumber;
    private String disputeType; // AMOUNT_DISCREPANCY, TIMING_DIFFERENCE, MISSING_TRANSACTION, DUPLICATE_TRANSACTION, CLASSIFICATION_ERROR
    private String status; // OPEN, INVESTIGATING, RESOLVED, ESCALATED, CLOSED
    private String priority; // LOW, MEDIUM, HIGH, CRITICAL
    private UUID sourceEntityId;
    private String sourceEntityName;
    private UUID targetEntityId;
    private String targetEntityName;
    private String accountCode;
    private String accountName;
    private UUID sourceTransactionId;
    private UUID targetTransactionId;
    private BigDecimal disputedAmount;
    private String currency;
    private LocalDate transactionDate;
    private LocalDate disputeRaisedDate;
    private String raisedBy;
    private String description;
    private String disputeReason;
    private List<String> supportingEvidence;
    private String currentOwner;
    private LocalDate targetResolutionDate;
    private String resolutionMethod; // ADJUSTMENT, REVERSAL, RECLASSIFICATION, NO_ACTION
    private String rootCause;
    private BigDecimal agreedAmount;
    private String resolutionNotes;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    private boolean requiresApproval;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private List<String> communications;
    private String escalationLevel;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private String updatedBy;
}
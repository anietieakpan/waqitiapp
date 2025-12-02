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
 * DTO for requesting dispute resolution
 * Contains information needed to initiate dispute resolution process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeResolutionRequest {
    
    private UUID requestId;
    private UUID disputeId;
    private String disputeType; // AMOUNT_DISCREPANCY, TIMING_DIFFERENCE, MISSING_TRANSACTION, DUPLICATE_TRANSACTION
    private String priority; // HIGH, MEDIUM, LOW, URGENT
    private UUID sourceEntityId;
    private String sourceEntityName;
    private UUID targetEntityId;
    private String targetEntityName;
    private String accountCode;
    private BigDecimal disputedAmount;
    private String currency;
    private LocalDate transactionDate;
    private String transactionReference;
    private String description;
    private String disputeReason;
    private List<String> supportingEvidence;
    private List<String> attachedDocuments;
    private String proposedResolution;
    private BigDecimal proposedAdjustmentAmount;
    private String requestedAction; // INVESTIGATE, ADJUST, REVERSE, CLARIFY
    private LocalDate requestedResolutionDate;
    private String assignedTo;
    private String requestedBy;
    private LocalDateTime requestedAt;
    private String businessJustification;
    private boolean requiresManagerialApproval;
    private String escalationPath;
    private String contactInformation;
    private String additionalNotes;
}
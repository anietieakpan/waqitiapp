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
 * DTO for adjustment entries
 * Represents journal entries created to correct discrepancies or errors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustmentEntry {
    
    private UUID adjustmentId;
    private String adjustmentNumber;
    private String adjustmentType; // RECONCILIATION_ADJUSTMENT, ERROR_CORRECTION, RECLASSIFICATION, ACCRUAL, REVERSAL
    private String reason; // TIMING_DIFFERENCE, AMOUNT_DISCREPANCY, CLASSIFICATION_ERROR, DUPLICATE_ENTRY
    private UUID sourceEntityId;
    private String sourceEntityName;
    private UUID targetEntityId;
    private String targetEntityName;
    private String accountCode;
    private String accountName;
    private BigDecimal adjustmentAmount;
    private String currency;
    private String debitCreditIndicator; // DEBIT, CREDIT
    private LocalDate effectiveDate;
    private LocalDate adjustmentDate;
    private String description;
    private String reference;
    private UUID originalTransactionId;
    private UUID relatedDisputeId;
    private UUID relatedDiscrepancyId;
    private String status; // DRAFT, PENDING_APPROVAL, APPROVED, POSTED, REJECTED
    private boolean requiresApproval;
    private String preparedBy;
    private LocalDateTime preparedAt;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private UUID journalEntryId;
    private LocalDateTime postedAt;
    private String postedBy;
    private List<String> supportingDocuments;
    private String businessJustification;
    private String notes;
    private LocalDateTime createdAt;
    private String createdBy;
}
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
 * DTO representing a single discrepancy item in inter-company reconciliation
 * Contains details about individual discrepancies found between entities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterCompanyDiscrepancy {
    
    private UUID discrepancyId;
    private String discrepancyType; // AMOUNT_MISMATCH, MISSING_TRANSACTION, TIMING_DIFFERENCE, ACCOUNT_MISMATCH
    private String priority; // HIGH, MEDIUM, LOW
    private String status; // OPEN, INVESTIGATING, RESOLVED, DISPUTED
    private UUID sourceEntityId;
    private String sourceEntityName;
    private UUID targetEntityId; 
    private String targetEntityName;
    private String sourceAccountCode;
    private String targetAccountCode;
    private UUID sourceTransactionId;
    private UUID targetTransactionId;
    private BigDecimal sourceAmount;
    private BigDecimal targetAmount;
    private BigDecimal discrepancyAmount;
    private String currency;
    private LocalDate transactionDate;
    private LocalDate identifiedDate;
    private String description;
    private String sourceReference;
    private String targetReference;
    private String rootCause;
    private String recommendedAction;
    private List<String> supportingDocuments;
    private boolean requiresAdjustment;
    private String assignedTo;
    private LocalDate dueDate;
    private String resolutionNotes;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime lastUpdated;
    private String updatedBy;
}
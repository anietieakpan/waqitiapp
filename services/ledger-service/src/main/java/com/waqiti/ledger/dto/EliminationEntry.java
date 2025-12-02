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
 * DTO for consolidation elimination entries
 * Represents entries that need to be eliminated during consolidation process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EliminationEntry {
    
    private UUID eliminationId;
    private String eliminationType; // INTERCOMPANY_SALES, INTERCOMPANY_RECEIVABLES, INTERCOMPANY_DIVIDENDS, INVESTMENT_ELIMINATION
    private UUID sourceEntityId;
    private String sourceEntityName;
    private UUID targetEntityId;
    private String targetEntityName;
    private String sourceAccountCode;
    private String targetAccountCode;
    private String sourceAccountName;
    private String targetAccountName;
    private BigDecimal eliminationAmount;
    private String currency;
    private LocalDate effectiveDate;
    private LocalDate consolidationDate;
    private String description;
    private String reference;
    private UUID sourceTransactionId;
    private UUID targetTransactionId;
    private String eliminationStatus; // PROPOSED, APPROVED, POSTED, REJECTED
    private String eliminationMethod; // AUTOMATIC, MANUAL, SYSTEMATIC
    private BigDecimal percentageEliminated;
    private boolean requiresApproval;
    private LocalDateTime approvedAt;
    private String approvedBy;
    private String approvalNotes;
    private UUID journalEntryId;
    private LocalDateTime postedAt;
    private String postedBy;
    private List<String> supportingDocuments;
    private String notes;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime lastUpdated;
    private String updatedBy;
}
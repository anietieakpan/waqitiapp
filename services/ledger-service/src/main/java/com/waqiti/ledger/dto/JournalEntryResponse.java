package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryResponse {
    
    private UUID journalEntryId;
    private String entryNumber;
    private String referenceNumber;
    private String entryType;
    private String description;
    private LocalDateTime entryDate;
    private LocalDateTime effectiveDate;
    private String status;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private String currency;
    private UUID accountingPeriodId;
    private String sourceSystem;
    private String sourceDocumentId;
    private String sourceDocumentType;
    private LocalDateTime postedAt;
    private String postedBy;
    private LocalDateTime reversedAt;
    private String reversedBy;
    private String reversalReason;
    private UUID originalJournalEntryId;
    private Boolean approvalRequired;
    private LocalDateTime approvedAt;
    private String approvedBy;
    private String approvalNotes;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private String createdBy;
    private String updatedBy;
    private List<LedgerEntryResponse> ledgerEntries;
    private boolean balanced;
    private boolean canBePosted;
    private boolean canBeReversed;
    private boolean requiresApproval;
    private boolean isReversalEntry;
    private boolean isPeriodEndEntry;
    private boolean isSystemGenerated;
}
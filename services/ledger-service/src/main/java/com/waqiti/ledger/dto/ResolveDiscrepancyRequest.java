package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveDiscrepancyRequest {
    
    @NotNull(message = "Discrepancy ID is required")
    private UUID discrepancyId;
    
    @NotNull(message = "Resolution method is required")
    private ResolutionMethod resolutionMethod;
    
    @NotBlank(message = "Resolution notes are required")
    private String resolutionNotes;
    
    private String resolvedBy;
    
    private List<ResolutionAction> actions;
    
    private BigDecimal adjustmentAmount;
    
    private UUID adjustmentAccountId;
    
    private String adjustmentReference;
    
    private LocalDate effectiveDate;
    
    private boolean createJournalEntry;
    
    private JournalEntryDetails journalEntryDetails;
    
    private List<UUID> attachmentIds;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ResolutionAction {
    private String actionType;
    private String actionDescription;
    private BigDecimal amount;
    private UUID accountId;
    private String reference;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class JournalEntryDetails {
    private String description;
    private String reference;
    private List<JournalLineItem> lineItems;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class JournalLineItem {
    private UUID accountId;
    private BigDecimal debit;
    private BigDecimal credit;
    private String description;
}
package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Represents a complete journal entry with all its line items.
 * This DTO is used for both creating and representing journal entries
 * in the accounting system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntry {
    
    /**
     * Unique identifier for the journal entry
     */
    private UUID journalEntryId;
    
    /**
     * System-generated entry number
     */
    private String entryNumber;
    
    /**
     * User-provided reference number
     */
    @NotBlank(message = "Reference number is required")
    @Size(max = 100, message = "Reference number must not exceed 100 characters")
    private String referenceNumber;
    
    /**
     * Type of journal entry (CLOSING, ADJUSTING, REVERSING, STANDARD)
     */
    @NotBlank(message = "Entry type is required")
    private String entryType;
    
    /**
     * Description of the journal entry
     */
    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
    
    /**
     * Date when the entry was created
     */
    @NotNull(message = "Entry date is required")
    private LocalDateTime entryDate;
    
    /**
     * Effective date for the journal entry
     */
    @NotNull(message = "Effective date is required")
    private LocalDateTime effectiveDate;
    
    /**
     * Current status of the entry (DRAFT, POSTED, APPROVED, REVERSED)
     */
    private String status;
    
    /**
     * Total debit amount (calculated from line items)
     */
    private BigDecimal totalDebits;
    
    /**
     * Total credit amount (calculated from line items)
     */
    private BigDecimal totalCredits;
    
    /**
     * Currency for the journal entry
     */
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;
    
    /**
     * Accounting period this entry belongs to
     */
    private UUID accountingPeriodId;
    
    /**
     * Source system that created this entry
     */
    private String sourceSystem;
    
    /**
     * Source document identifier
     */
    private String sourceDocumentId;
    
    /**
     * Type of source document
     */
    private String sourceDocumentType;
    
    /**
     * When the entry was posted to the ledger
     */
    private LocalDateTime postedAt;
    
    /**
     * User who posted the entry
     */
    private String postedBy;
    
    /**
     * When the entry was reversed (if applicable)
     */
    private LocalDateTime reversedAt;
    
    /**
     * User who reversed the entry
     */
    private String reversedBy;
    
    /**
     * Reason for reversal
     */
    private String reversalReason;
    
    /**
     * Original journal entry ID (if this is a reversal)
     */
    private UUID originalJournalEntryId;
    
    /**
     * Whether this entry requires approval before posting
     */
    @Builder.Default
    private Boolean approvalRequired = false;
    
    /**
     * When the entry was approved
     */
    private LocalDateTime approvedAt;
    
    /**
     * User who approved the entry
     */
    private String approvedBy;
    
    /**
     * Approval notes or comments
     */
    private String approvalNotes;
    
    /**
     * Additional metadata for the entry
     */
    private String metadata;
    
    /**
     * When the entry was created
     */
    private LocalDateTime createdAt;
    
    /**
     * When the entry was last updated
     */
    private LocalDateTime lastUpdated;
    
    /**
     * User who created the entry
     */
    private String createdBy;
    
    /**
     * User who last updated the entry
     */
    private String updatedBy;
    
    /**
     * List of journal entry line items
     */
    @NotEmpty(message = "At least one journal entry line is required")
    @Valid
    private List<JournalEntryLine> journalEntryLines;
    
    /**
     * Whether the journal entry is balanced (debits = credits)
     */
    private boolean balanced;
    
    /**
     * Whether the entry can be posted
     */
    private boolean canBePosted;
    
    /**
     * Whether the entry can be reversed
     */
    private boolean canBeReversed;
    
    /**
     * Whether the entry requires approval
     */
    private boolean requiresApproval;
    
    /**
     * Whether this is a reversal entry
     */
    private boolean isReversalEntry;
    
    /**
     * Whether this is a period-end closing entry
     */
    private boolean isPeriodEndEntry;
    
    /**
     * Whether this entry was system-generated
     */
    private boolean isSystemGenerated;
    
    /**
     * Whether this is a recurring entry
     */
    private boolean isRecurring;
    
    /**
     * Recurring schedule information (if applicable)
     */
    private String recurringSchedule;
    
    /**
     * Next occurrence date (if recurring)
     */
    private LocalDateTime nextOccurrenceDate;
    
    /**
     * Priority level for processing (HIGH, MEDIUM, LOW)
     */
    private String priority;
    
    /**
     * Batch ID if this entry is part of a batch
     */
    private UUID batchId;
    
    /**
     * Any validation warnings for this entry
     */
    private List<String> validationWarnings;
    
    /**
     * Any validation errors for this entry
     */
    private List<String> validationErrors;
}
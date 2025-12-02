package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Represents a reversing journal entry that will automatically reverse
 * in the next accounting period. Commonly used for accruals and deferrals
 * that need to be reversed at the beginning of the next period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReversingEntry {
    
    /**
     * Unique identifier for the reversing entry
     */
    private UUID reversingEntryId;
    
    /**
     * Original journal entry that this reverses
     */
    @NotNull(message = "Original journal entry ID is required")
    private UUID originalJournalEntryId;
    
    /**
     * New journal entry created for the reversal
     */
    private UUID reversingJournalEntryId;
    
    /**
     * Period where the original entry was made
     */
    @NotNull(message = "Original period ID is required")
    private UUID originalPeriodId;
    
    /**
     * Period where the reversing entry will be made
     */
    @NotNull(message = "Reversing period ID is required")
    private UUID reversingPeriodId;
    
    /**
     * Date when the original entry was made
     */
    private LocalDate originalEntryDate;
    
    /**
     * Date when the reversal should occur
     */
    @NotNull(message = "Reversal date is required")
    private LocalDate reversalDate;
    
    /**
     * Type of reversing entry (AUTO_REVERSE, MANUAL_REVERSE)
     */
    private String reversingType;
    
    /**
     * Reason for the reversal
     */
    private String reversalReason;
    
    /**
     * Description of the reversing entry
     */
    private String description;
    
    /**
     * Status of the reversing entry (PENDING, PROCESSED, CANCELLED)
     */
    private String status;
    
    /**
     * Total amount being reversed
     */
    private BigDecimal totalAmount;
    
    /**
     * Currency of the reversing entry
     */
    private String currency;
    
    /**
     * Details of the reversing entry lines
     */
    private List<ReversingEntryLine> reversingLines;
    
    /**
     * Whether this reversal has been automatically processed
     */
    private boolean isAutoProcessed;
    
    /**
     * When the reversal was scheduled
     */
    private LocalDateTime scheduledAt;
    
    /**
     * When the reversal was actually processed
     */
    private LocalDateTime processedAt;
    
    /**
     * User who scheduled the reversal
     */
    private String scheduledBy;
    
    /**
     * User who processed the reversal
     */
    private String processedBy;
    
    /**
     * When this reversing entry record was created
     */
    private LocalDateTime createdAt;
    
    /**
     * When this record was last updated
     */
    private LocalDateTime lastUpdated;
    
    /**
     * User who created this reversing entry
     */
    private String createdBy;
    
    /**
     * User who last updated this record
     */
    private String updatedBy;
    
    /**
     * Any errors encountered during reversal processing
     */
    private List<String> processingErrors;
    
    /**
     * Any warnings generated during reversal processing
     */
    private List<String> processingWarnings;
    
    /**
     * Additional metadata for the reversing entry
     */
    private String metadata;
    
    /**
     * Whether this reversal can be cancelled
     */
    private boolean canBeCancelled;
    
    /**
     * Whether this reversal is part of a batch process
     */
    private boolean isBatchProcessed;
    
    /**
     * Batch ID if part of batch processing
     */
    private UUID batchId;
    
    /**
     * Priority for processing (HIGH, MEDIUM, LOW)
     */
    private String priority;
    
    /**
     * Number of retry attempts (for failed reversals)
     */
    private int retryCount;
    
    /**
     * Maximum retry attempts allowed
     */
    private int maxRetryAttempts;
    
    /**
     * Next retry attempt time (if applicable)
     */
    private LocalDateTime nextRetryAt;
}

/**
 * Individual line item within a reversing entry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ReversingEntryLine {
    
    /**
     * Unique identifier for this reversing line
     */
    private UUID reversingLineId;
    
    /**
     * Parent reversing entry ID
     */
    private UUID reversingEntryId;
    
    /**
     * Original journal entry line being reversed
     */
    private UUID originalJournalEntryLineId;
    
    /**
     * New journal entry line for the reversal
     */
    private UUID reversingJournalEntryLineId;
    
    /**
     * Account being affected by the reversal
     */
    private UUID accountId;
    
    /**
     * Account code for reference
     */
    private String accountCode;
    
    /**
     * Account name for reference
     */
    private String accountName;
    
    /**
     * Original entry type (DEBIT/CREDIT)
     */
    private String originalEntryType;
    
    /**
     * Reversing entry type (opposite of original)
     */
    private String reversingEntryType;
    
    /**
     * Amount being reversed
     */
    private BigDecimal amount;
    
    /**
     * Description for this line
     */
    private String description;
    
    /**
     * Currency for this line
     */
    private String currency;
    
    /**
     * Status of this line (PENDING, PROCESSED, FAILED)
     */
    private String status;
    
    /**
     * Any processing errors for this line
     */
    private String processingError;
}
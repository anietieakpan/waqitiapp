package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single line item within a journal entry.
 * Each line represents either a debit or credit to a specific account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntryLine {
    
    /**
     * Unique identifier for the journal entry line
     */
    private UUID journalEntryLineId;
    
    /**
     * Parent journal entry identifier
     */
    @NotNull(message = "Journal entry ID is required")
    private UUID journalEntryId;
    
    /**
     * Line number within the journal entry (for ordering)
     */
    private Integer lineNumber;
    
    /**
     * Account being debited or credited
     */
    @NotNull(message = "Account ID is required")
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
     * Type of entry (DEBIT or CREDIT)
     */
    @NotBlank(message = "Entry type is required")
    private String entryType;
    
    /**
     * Amount for this line item (always positive, sign determined by entryType)
     */
    @NotNull(message = "Amount is required")
    private BigDecimal amount;
    
    /**
     * Running balance after this line item (if available)
     */
    private BigDecimal runningBalance;
    
    /**
     * Description specific to this line item
     */
    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
    
    /**
     * Additional narrative or explanation for this line
     */
    private String narrative;
    
    /**
     * Currency for this line item
     */
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;
    
    /**
     * Exchange rate used (if different from base currency)
     */
    private BigDecimal exchangeRate;
    
    /**
     * Amount in base currency (if applicable)
     */
    private BigDecimal baseCurrencyAmount;
    
    /**
     * Contra account ID (if applicable)
     */
    private UUID contraAccountId;
    
    /**
     * Reference number specific to this line
     */
    private String referenceNumber;
    
    /**
     * Cost center or department code
     */
    private String costCenter;
    
    /**
     * Project or job code
     */
    private String projectCode;
    
    /**
     * Business unit or division
     */
    private String businessUnit;
    
    /**
     * Tax code or tax treatment
     */
    private String taxCode;
    
    /**
     * Tax amount (if applicable)
     */
    private BigDecimal taxAmount;
    
    /**
     * Tax rate applied (if applicable)
     */
    private BigDecimal taxRate;
    
    /**
     * Quantity (if this line represents quantity-based transactions)
     */
    private BigDecimal quantity;
    
    /**
     * Unit of measure for quantity
     */
    private String unitOfMeasure;
    
    /**
     * Unit price (if applicable)
     */
    private BigDecimal unitPrice;
    
    /**
     * Vendor or customer ID (if applicable)
     */
    private UUID vendorCustomerId;
    
    /**
     * Invoice or document number
     */
    private String invoiceNumber;
    
    /**
     * Due date (for payables/receivables)
     */
    private LocalDateTime dueDate;
    
    /**
     * Terms or payment terms
     */
    private String terms;
    
    /**
     * Status of this line item
     */
    private String status;
    
    /**
     * Whether this line item is system-generated
     */
    private boolean isSystemGenerated;
    
    /**
     * Whether this line item has been reconciled
     */
    private boolean isReconciled;
    
    /**
     * Reconciliation date
     */
    private LocalDateTime reconciledAt;
    
    /**
     * User who performed reconciliation
     */
    private String reconciledBy;
    
    /**
     * Additional metadata for this line item
     */
    private String metadata;
    
    /**
     * When this line item was created
     */
    private LocalDateTime createdAt;
    
    /**
     * When this line item was last updated
     */
    private LocalDateTime lastUpdated;
    
    /**
     * User who created this line item
     */
    private String createdBy;
    
    /**
     * User who last updated this line item
     */
    private String updatedBy;
    
    /**
     * Any validation warnings for this line item
     */
    private String validationWarnings;
    
    /**
     * Any validation errors for this line item
     */
    private String validationErrors;
    
    /**
     * Whether this line item is part of a reversing entry
     */
    private boolean isReversingEntry;
    
    /**
     * Original line item ID (if this is a reversal)
     */
    private UUID originalLineItemId;
    
    /**
     * Allocation percentage (for cost allocation entries)
     */
    private BigDecimal allocationPercentage;
    
    /**
     * Allocation basis (for cost allocation entries)
     */
    private String allocationBasis;
}
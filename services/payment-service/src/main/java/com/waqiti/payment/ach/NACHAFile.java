package com.waqiti.payment.ach;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * NACHA (National Automated Clearing House Association) File DTO
 *
 * Represents a complete NACHA format file for ACH transaction processing.
 * Complies with NACHA Operating Rules & Guidelines.
 *
 * NACHA File Structure:
 * - File Header Record (Type 1)
 * - Company/Batch Header Record (Type 5)
 * - Entry Detail Records (Type 6)
 * - Addenda Records (Type 7) [Optional]
 * - Company/Batch Control Record (Type 8)
 * - File Control Record (Type 9)
 *
 * Standards: NACHA Operating Rules & Guidelines
 * @author Waqiti Payment Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NACHAFile {

    /**
     * Unique file identifier
     */
    private String fileId;

    /**
     * Batch identifier linking this file to specific transfers
     */
    private String batchId;

    /**
     * Complete NACHA-formatted file content (94-character records)
     */
    private String fileContent;

    /**
     * File name following NACHA naming convention
     * Format: [CompanyID]_[YYYYMMDD]_[HHMMSS].ach
     */
    private String fileName;

    /**
     * SHA-256 hash of file content for integrity verification
     */
    private String fileHash;

    /**
     * Total number of records in the file
     */
    private int totalRecords;

    /**
     * Total number of entry detail records (transactions)
     */
    private int totalEntries;

    /**
     * Total debit amount across all transactions
     */
    private java.math.BigDecimal totalDebitAmount;

    /**
     * Total credit amount across all transactions
     */
    private java.math.BigDecimal totalCreditAmount;

    /**
     * File creation date
     */
    private LocalDate fileCreationDate;

    /**
     * File creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Effective entry date (settlement date)
     */
    private LocalDate effectiveEntryDate;

    /**
     * Immediate destination (receiving financial institution routing number)
     */
    private String immediateDestination;

    /**
     * Immediate origin (sending financial institution routing number)
     */
    private String immediateOrigin;

    /**
     * File validation status
     */
    private boolean valid;

    /**
     * Validation errors (if any)
     */
    private List<String> validationErrors;

    /**
     * File size in bytes
     */
    private long fileSizeBytes;

    /**
     * Standard Entry Class Code (e.g., PPD, CCD, WEB)
     */
    private String standardEntryClassCode;

    /**
     * Company name originating the ACH transactions
     */
    private String companyName;

    /**
     * Company identification (Tax ID)
     */
    private String companyId;

    /**
     * Indicates if file has been submitted to ACH network
     */
    private boolean submitted;

    /**
     * Submission timestamp
     */
    private LocalDateTime submittedAt;

    /**
     * ACH network confirmation number
     */
    private String confirmationNumber;

    /**
     * Validates NACHA file structure and content
     *
     * @return true if file passes all validation checks
     */
    public boolean isValid() {
        return valid && (validationErrors == null || validationErrors.isEmpty());
    }

    /**
     * Gets total transaction count
     */
    public int getTotalTransactionCount() {
        return totalEntries;
    }

    /**
     * Gets net amount (credits - debits)
     */
    public java.math.BigDecimal getNetAmount() {
        if (totalCreditAmount == null) totalCreditAmount = java.math.BigDecimal.ZERO;
        if (totalDebitAmount == null) totalDebitAmount = java.math.BigDecimal.ZERO;
        return totalCreditAmount.subtract(totalDebitAmount);
    }
}

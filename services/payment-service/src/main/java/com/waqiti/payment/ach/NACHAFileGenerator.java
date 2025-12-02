package com.waqiti.payment.ach;

import com.waqiti.payment.entity.ACHTransfer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * NACHA File Generator Service
 *
 * Generates NACHA-compliant ACH files following the NACHA Operating Rules & Guidelines.
 * Each line in a NACHA file is exactly 94 characters.
 *
 * Record Types:
 * - Type 1: File Header Record
 * - Type 5: Company/Batch Header Record
 * - Type 6: Entry Detail Records (transactions)
 * - Type 7: Addenda Records (optional)
 * - Type 8: Company/Batch Control Record
 * - Type 9: File Control Record
 *
 * Standards: NACHA Operating Rules & Guidelines
 * PCI DSS: Account numbers are already encrypted in ACHTransfer entity
 *
 * @author Waqiti Payment Team
 * @version 2.0
 * @since 2025-10-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NACHAFileGenerator {

    @Value("${nacha.immediate-destination:091000019}")
    private String immediateDestination; // Federal Reserve Bank routing number

    @Value("${nacha.immediate-origin:123456789}")
    private String immediateOrigin; // Company routing number

    @Value("${nacha.company-name:WAQITI PAYMENTS}")
    private String companyName;

    @Value("${nacha.company-id:1234567890}")
    private String companyId; // Tax ID

    @Value("${nacha.company-entry-description:PAYROLL}")
    private String companyEntryDescription;

    @Value("${nacha.standard-entry-class:PPD}")
    private String standardEntryClassCode; // PPD = Prearranged Payment & Deposit

    private final FederalHolidayService holidayService;

    private static final int NACHA_RECORD_LENGTH = 94;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm");

    /**
     * Generates a complete NACHA file for a batch of ACH transfers
     *
     * @param batchId Unique batch identifier
     * @param transfers List of ACH transfers to include in file
     * @return Complete NACHA file ready for ACH network submission
     */
    public NACHAFile generateFile(String batchId, List<ACHTransfer> transfers) {
        log.info("Generating NACHA file for batch: {} with {} transfers", batchId, transfers.size());

        try {
            // Validate inputs
            if (transfers == null || transfers.isEmpty()) {
                throw new IllegalArgumentException("Cannot generate NACHA file with no transfers");
            }

            // Calculate effective entry date (next business day)
            LocalDate effectiveDate = calculateEffectiveDate(LocalDate.now());

            // Build NACHA file content
            StringBuilder fileContent = new StringBuilder();
            List<String> validationErrors = new ArrayList<>();

            // 1. File Header Record (Type 1)
            String fileHeader = buildFileHeaderRecord();
            fileContent.append(fileHeader).append("\n");

            // 2. Company/Batch Header Record (Type 5)
            String batchHeader = buildBatchHeaderRecord(batchId, effectiveDate, transfers.size());
            fileContent.append(batchHeader).append("\n");

            // 3. Entry Detail Records (Type 6)
            BigDecimal totalDebitAmount = BigDecimal.ZERO;
            BigDecimal totalCreditAmount = BigDecimal.ZERO;
            int entryCount = 0;
            String entryHash = "";

            for (ACHTransfer transfer : transfers) {
                String entryRecord = buildEntryDetailRecord(transfer, entryCount + 1);
                fileContent.append(entryRecord).append("\n");

                // Accumulate totals
                if ("DEBIT".equals(transfer.getDirection().name())) {
                    totalDebitAmount = totalDebitAmount.add(transfer.getAmount());
                } else {
                    totalCreditAmount = totalCreditAmount.add(transfer.getAmount());
                }

                entryCount++;
            }

            // Calculate entry hash (sum of first 8 digits of routing numbers)
            long hashSum = transfers.stream()
                    .mapToLong(t -> Long.parseLong(t.getRoutingNumber().substring(0, 8)))
                    .sum();
            entryHash = String.format("%010d", hashSum % 10000000000L);

            // 4. Company/Batch Control Record (Type 8)
            String batchControl = buildBatchControlRecord(
                    entryCount, entryHash, totalDebitAmount, totalCreditAmount);
            fileContent.append(batchControl).append("\n");

            // 5. File Control Record (Type 9)
            String fileControl = buildFileControlRecord(
                    1, entryCount, entryHash, totalDebitAmount, totalCreditAmount);
            fileContent.append(fileControl).append("\n");

            // Add padding records to make file block size = 10 (NACHA requirement)
            int totalRecords = 5 + entryCount; // Header + Batch Header + Entries + Batch Control + File Control
            int paddingNeeded = (10 - (totalRecords % 10)) % 10;
            for (int i = 0; i < paddingNeeded; i++) {
                fileContent.append(String.format("%-94s", "9").replace(' ', '9')).append("\n");
            }

            // Calculate file hash for integrity verification
            String fileHash = calculateSHA256(fileContent.toString());

            // Validate file structure
            boolean isValid = validateNACHAFile(fileContent.toString(), validationErrors);

            // Generate file name
            String fileName = String.format("%s_%s_%s.ach",
                    companyId,
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
            );

            // Build NACHA file object
            NACHAFile nachaFile = NACHAFile.builder()
                    .fileId(UUID.randomUUID().toString())
                    .batchId(batchId)
                    .fileContent(fileContent.toString())
                    .fileName(fileName)
                    .fileHash(fileHash)
                    .totalRecords(totalRecords + paddingNeeded)
                    .totalEntries(entryCount)
                    .totalDebitAmount(totalDebitAmount)
                    .totalCreditAmount(totalCreditAmount)
                    .fileCreationDate(LocalDate.now())
                    .createdAt(LocalDateTime.now())
                    .effectiveEntryDate(effectiveDate)
                    .immediateDestination(immediateDestination)
                    .immediateOrigin(immediateOrigin)
                    .valid(isValid)
                    .validationErrors(validationErrors)
                    .fileSizeBytes(fileContent.length())
                    .standardEntryClassCode(standardEntryClassCode)
                    .companyName(companyName)
                    .companyId(companyId)
                    .submitted(false)
                    .build();

            log.info("NACHA file generated successfully: {}, entries: {}, valid: {}",
                    fileName, entryCount, isValid);

            return nachaFile;

        } catch (Exception e) {
            log.error("Failed to generate NACHA file for batch: {}", batchId, e);
            throw new RuntimeException("NACHA file generation failed", e);
        }
    }

    /**
     * Builds File Header Record (Type 1) - 94 characters
     */
    private String buildFileHeaderRecord() {
        LocalDate now = LocalDate.now();
        LocalDateTime nowTime = LocalDateTime.now();

        return String.format("1%2s%-23s%-23s%6s%4s%1s%3s%-23s%-23s%8s",
                "01",                                    // Priority Code
                immediateDestination,                    // Immediate Destination (10 chars)
                immediateOrigin,                         // Immediate Origin (10 chars)
                now.format(DATE_FORMAT),                 // File Creation Date
                nowTime.format(TIME_FORMAT),             // File Creation Time
                "A",                                     // File ID Modifier
                "094",                                   // Record Size (94 chars)
                "10",                                    // Blocking Factor
                "1",                                     // Format Code
                immediateDestination.substring(0, Math.min(23, immediateDestination.length())), // Destination Name
                immediateOrigin.substring(0, Math.min(23, immediateOrigin.length())),          // Origin Name
                "        "                               // Reference Code (8 spaces)
        );
    }

    /**
     * Builds Batch Header Record (Type 5) - 94 characters
     */
    private String buildBatchHeaderRecord(String batchId, LocalDate effectiveDate, int entryCount) {
        return String.format("5%3s%-16s%-20s%10s%6s%-8s%6s%-15s%3s%1s%s",
                standardEntryClassCode,                  // Standard Entry Class Code
                companyName.substring(0, Math.min(16, companyName.length())),  // Company Name
                companyEntryDescription,                 // Company Discretionary Data
                companyId,                               // Company Identification
                standardEntryClassCode,                  // Standard Entry Class Code (duplicate)
                companyEntryDescription,                 // Company Entry Description
                effectiveDate.format(DATE_FORMAT),       // Effective Entry Date
                "   ",                                   // Settlement Date (Julian, 3 spaces)
                "1",                                     // Originator Status Code
                immediateOrigin.substring(0, 8),         // Originating DFI Identification
                String.format("%07d", 1)                 // Batch Number
        );
    }

    /**
     * Builds Entry Detail Record (Type 6) - 94 characters
     */
    private String buildEntryDetailRecord(ACHTransfer transfer, int traceNumber) {
        String transactionCode = determineTransactionCode(transfer);

        return String.format("6%2s%8s%17s%10s%-22s%-15s%2s%8s%15s",
                transactionCode,                         // Transaction Code
                transfer.getRoutingNumber().substring(0, 8),  // Receiving DFI Identification
                transfer.getRoutingNumber().charAt(8),   // Check Digit
                transfer.getAccountNumber(),             // DFI Account Number
                formatAmount(transfer.getAmount()),      // Amount
                transfer.getId().toString().substring(0, 15),  // Individual ID
                transfer.getAccountHolderName().substring(0, Math.min(22, transfer.getAccountHolderName().length())),  // Individual Name
                "  ",                                    // Discretionary Data
                "0",                                     // Addenda Record Indicator
                immediateOrigin.substring(0, 8) + String.format("%07d", traceNumber)  // Trace Number
        );
    }

    /**
     * Builds Batch Control Record (Type 8) - 94 characters
     */
    private String buildBatchControlRecord(int entryCount, String entryHash,
                                          BigDecimal totalDebit, BigDecimal totalCredit) {
        return String.format("8%3s%06d%10s%12s%12s%10s%25s%8s%6s",
                standardEntryClassCode,                  // Service Class Code
                entryCount,                              // Entry/Addenda Count
                entryHash,                               // Entry Hash
                formatAmount(totalDebit),                // Total Debit Amount
                formatAmount(totalCredit),               // Total Credit Amount
                companyId,                               // Company Identification
                " ".repeat(25),                          // Message Authentication Code
                " ".repeat(6),                           // Reserved
                immediateOrigin.substring(0, 8) + String.format("%07d", 1)  // Originating DFI + Batch Number
        );
    }

    /**
     * Builds File Control Record (Type 9) - 94 characters
     */
    private String buildFileControlRecord(int batchCount, int entryCount, String entryHash,
                                         BigDecimal totalDebit, BigDecimal totalCredit) {
        return String.format("9%06d%06d%08d%010s%012s%012s%39s",
                batchCount,                              // Batch Count
                Math.min(999999, (entryCount + 9) / 10), // Block Count
                entryCount,                              // Entry/Addenda Count
                entryHash,                               // Entry Hash
                formatAmount(totalDebit),                // Total Debit Amount
                formatAmount(totalCredit),               // Total Credit Amount
                " ".repeat(39)                           // Reserved
        );
    }

    /**
     * Determines transaction code based on transfer direction and account type
     */
    private String determineTransactionCode(ACHTransfer transfer) {
        boolean isCredit = "CREDIT".equals(transfer.getDirection().name());
        boolean isChecking = "CHECKING".equals(transfer.getAccountType().name());

        if (isCredit && isChecking) return "22"; // Checking Credit
        if (isCredit && !isChecking) return "32"; // Savings Credit
        if (!isCredit && isChecking) return "27"; // Checking Debit
        return "37"; // Savings Debit
    }

    /**
     * Formats amount to 10-character string (in cents)
     */
    private String formatAmount(BigDecimal amount) {
        long cents = amount.multiply(BigDecimal.valueOf(100)).longValue();
        return String.format("%010d", cents);
    }

    /**
     * Calculates effective entry date (next business day)
     */
    private LocalDate calculateEffectiveDate(LocalDate fromDate) {
        LocalDate effectiveDate = fromDate.plusDays(1);

        // Skip weekends and federal holidays
        while (effectiveDate.getDayOfWeek().getValue() > 5 ||
                holidayService.isFederalHoliday(effectiveDate)) {
            effectiveDate = effectiveDate.plusDays(1);
        }

        return effectiveDate;
    }

    /**
     * Validates NACHA file structure
     */
    private boolean validateNACHAFile(String fileContent, List<String> errors) {
        String[] lines = fileContent.split("\n");

        // Check each line is exactly 94 characters
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].length() != NACHA_RECORD_LENGTH) {
                errors.add(String.format("Line %d has invalid length: %d (expected 94)",
                        i + 1, lines[i].length()));
            }
        }

        // Check file starts with File Header (Type 1)
        if (!lines[0].startsWith("1")) {
            errors.add("File must start with File Header Record (Type 1)");
        }

        // Check file ends with File Control (Type 9) or padding (9's)
        if (!lines[lines.length - 1].startsWith("9")) {
            errors.add("File must end with File Control Record (Type 9) or padding");
        }

        return errors.isEmpty();
    }

    /**
     * Calculates SHA-256 hash of file content
     */
    private String calculateSHA256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to calculate SHA-256 hash", e);
            return "";
        }
    }
}

package com.waqiti.ledger.service;

import com.waqiti.ledger.domain.EntryType;
import com.waqiti.ledger.domain.JournalEntry;
import com.waqiti.ledger.domain.LedgerEntry;
import com.waqiti.ledger.exception.DoubleEntryValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Production-grade service for validating double-entry bookkeeping rules.
 * Ensures accounting equation balance and prevents data integrity issues.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DoubleEntryValidationService {
    
    private static final BigDecimal ZERO_THRESHOLD = new BigDecimal("0.01");
    private static final int PRECISION = 4;
    
    /**
     * Validates that a journal entry maintains double-entry bookkeeping balance.
     * Ensures total debits equal total credits.
     * 
     * @param journalEntry The journal entry to validate
     * @throws DoubleEntryValidationException if validation fails
     */
    public void validateJournalEntry(JournalEntry journalEntry) {
        log.debug("Validating journal entry: {}", journalEntry.getId());
        
        if (journalEntry == null) {
            throw new DoubleEntryValidationException("Journal entry cannot be null");
        }
        
        List<LedgerEntry> entries = journalEntry.getLedgerEntries();
        
        if (entries == null || entries.isEmpty()) {
            throw new DoubleEntryValidationException("Journal entry must have at least one ledger entry");
        }
        
        if (entries.size() < 2) {
            throw new DoubleEntryValidationException(
                "Journal entry must have at least 2 ledger entries for double-entry bookkeeping"
            );
        }
        
        // Calculate total debits and credits using enum-based validation
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        
        for (LedgerEntry entry : entries) {
            validateLedgerEntry(entry);
            
            // Use enum-based type checking to prevent bypass
            EntryType entryType = EntryType.fromString(entry.getEntryType());
            BigDecimal amount = entry.getAmount().abs().setScale(PRECISION, RoundingMode.HALF_UP);
            
            if (entryType.isDebit()) {
                totalDebits = totalDebits.add(amount);
            } else if (entryType.isCredit()) {
                totalCredits = totalCredits.add(amount);
            }
        }
        
        // Validate balance with precision tolerance
        BigDecimal difference = totalDebits.subtract(totalCredits).abs();
        
        if (difference.compareTo(ZERO_THRESHOLD) > 0) {
            throw new DoubleEntryValidationException(
                String.format("Double-entry validation failed: Debits (%s) != Credits (%s), Difference: %s",
                    totalDebits, totalCredits, difference)
            );
        }
        
        // Additional validations
        validateAccountTypes(entries);
        validateTransactionDate(journalEntry);
        validateCurrency(entries);
        
        log.info("Journal entry validated successfully: {} (Debits: {}, Credits: {})", 
                journalEntry.getId(), totalDebits, totalCredits);
    }
    
    /**
     * Validates a single ledger entry.
     * 
     * @param entry The ledger entry to validate
     * @throws DoubleEntryValidationException if validation fails
     */
    private void validateLedgerEntry(LedgerEntry entry) {
        if (entry == null) {
            throw new DoubleEntryValidationException("Ledger entry cannot be null");
        }
        
        if (entry.getAccountId() == null) {
            throw new DoubleEntryValidationException("Account ID is required for ledger entry");
        }
        
        if (entry.getAmount() == null || entry.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DoubleEntryValidationException(
                "Ledger entry amount must be positive: " + entry.getAmount()
            );
        }
        
        if (entry.getEntryType() == null || entry.getEntryType().trim().isEmpty()) {
            throw new DoubleEntryValidationException("Entry type is required");
        }
        
        // Validate entry type using enum
        try {
            EntryType.fromString(entry.getEntryType());
        } catch (IllegalArgumentException e) {
            throw new DoubleEntryValidationException(
                "Invalid entry type: " + entry.getEntryType() + ". " + e.getMessage()
            );
        }
    }
    
    /**
     * Validates that account types are appropriate for the transaction.
     * 
     * @param entries The ledger entries to validate
     */
    private void validateAccountTypes(List<LedgerEntry> entries) {
        // Group entries by type
        Map<EntryType, List<LedgerEntry>> entriesByType = entries.stream()
                .collect(Collectors.groupingBy(e -> EntryType.fromString(e.getEntryType())));
        
        // Ensure we have both debits and credits
        if (!entriesByType.containsKey(EntryType.DEBIT)) {
            throw new DoubleEntryValidationException("Journal entry must contain at least one debit");
        }
        
        if (!entriesByType.containsKey(EntryType.CREDIT)) {
            throw new DoubleEntryValidationException("Journal entry must contain at least one credit");
        }
        
        // Validate account compatibility
        for (LedgerEntry entry : entries) {
            validateAccountCompatibility(entry);
        }
    }
    
    /**
     * Validates that an account is compatible with its entry type.
     * 
     * @param entry The ledger entry to validate
     */
    private void validateAccountCompatibility(LedgerEntry entry) {
        // This would check against account configuration
        // For example, certain accounts may only allow debits or credits
        // Implementation depends on account structure
        
        if (entry.getAccountType() != null) {
            EntryType entryType = EntryType.fromString(entry.getEntryType());
            
            // Example validation rules (customize based on business logic)
            switch (entry.getAccountType()) {
                case "ASSET":
                case "EXPENSE":
                    // Assets and expenses normally increase with debits
                    if (entry.isReversal() && entryType.isDebit()) {
                        log.warn("Unusual entry: Debit reversal on {} account", entry.getAccountType());
                    }
                    break;
                    
                case "LIABILITY":
                case "EQUITY":
                case "REVENUE":
                    // Liabilities, equity, and revenue normally increase with credits
                    if (entry.isReversal() && entryType.isCredit()) {
                        log.warn("Unusual entry: Credit reversal on {} account", entry.getAccountType());
                    }
                    break;
                    
                default:
                    log.debug("Unknown account type: {}", entry.getAccountType());
            }
        }
    }
    
    /**
     * Validates the transaction date of a journal entry.
     * 
     * @param journalEntry The journal entry to validate
     */
    private void validateTransactionDate(JournalEntry journalEntry) {
        if (journalEntry.getTransactionDate() == null) {
            throw new DoubleEntryValidationException("Transaction date is required");
        }
        
        // Prevent future-dated entries (configurable based on business rules)
        if (journalEntry.getTransactionDate().isAfter(java.time.Instant.now().plusSeconds(86400))) {
            throw new DoubleEntryValidationException(
                "Transaction date cannot be more than 1 day in the future"
            );
        }
        
        // Prevent very old entries (configurable based on business rules)
        if (journalEntry.getTransactionDate().isBefore(
                java.time.Instant.now().minus(java.time.Duration.ofDays(365)))) {
            log.warn("Journal entry dated more than 1 year in the past: {}", 
                    journalEntry.getTransactionDate());
        }
    }
    
    /**
     * Validates that all entries use the same currency.
     * 
     * @param entries The ledger entries to validate
     */
    private void validateCurrency(List<LedgerEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        
        String firstCurrency = entries.get(0).getCurrency();
        if (firstCurrency == null) {
            throw new DoubleEntryValidationException("Currency is required for all ledger entries");
        }
        
        boolean allSameCurrency = entries.stream()
                .allMatch(e -> firstCurrency.equals(e.getCurrency()));
        
        if (!allSameCurrency) {
            // Multi-currency transactions require special handling
            validateMultiCurrencyTransaction(entries);
        }
    }
    
    /**
     * Validates multi-currency transactions.
     * 
     * @param entries The ledger entries with multiple currencies
     */
    private void validateMultiCurrencyTransaction(List<LedgerEntry> entries) {
        // Multi-currency transactions must balance in base currency
        Map<String, List<LedgerEntry>> entriesByCurrency = entries.stream()
                .collect(Collectors.groupingBy(LedgerEntry::getCurrency));
        
        if (entriesByCurrency.size() > 2) {
            throw new DoubleEntryValidationException(
                "Multi-currency transactions limited to 2 currencies"
            );
        }
        
        // Ensure exchange rate is provided
        for (LedgerEntry entry : entries) {
            if (entry.getExchangeRate() == null || entry.getExchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
                throw new DoubleEntryValidationException(
                    "Exchange rate required for multi-currency transaction"
                );
            }
        }
        
        // Validate base currency balance
        BigDecimal totalDebitsBase = BigDecimal.ZERO;
        BigDecimal totalCreditsBase = BigDecimal.ZERO;
        
        for (LedgerEntry entry : entries) {
            EntryType entryType = EntryType.fromString(entry.getEntryType());
            BigDecimal baseAmount = entry.getAmount()
                    .multiply(entry.getExchangeRate())
                    .setScale(PRECISION, RoundingMode.HALF_UP);
            
            if (entryType.isDebit()) {
                totalDebitsBase = totalDebitsBase.add(baseAmount);
            } else {
                totalCreditsBase = totalCreditsBase.add(baseAmount);
            }
        }
        
        BigDecimal differenceBase = totalDebitsBase.subtract(totalCreditsBase).abs();
        
        if (differenceBase.compareTo(ZERO_THRESHOLD) > 0) {
            throw new DoubleEntryValidationException(
                String.format("Multi-currency validation failed: Base currency imbalance: %s", 
                    differenceBase)
            );
        }
    }
    
    /**
     * Validates a batch of journal entries for consistency.
     * 
     * @param journalEntries List of journal entries to validate
     * @return Validation result with any warnings
     */
    public ValidationResult validateBatch(List<JournalEntry> journalEntries) {
        ValidationResult result = new ValidationResult();
        
        for (JournalEntry entry : journalEntries) {
            try {
                validateJournalEntry(entry);
                result.addValid(entry.getId());
            } catch (DoubleEntryValidationException e) {
                result.addError(entry.getId(), e.getMessage());
            }
        }
        
        // Check for duplicate entries
        checkForDuplicates(journalEntries, result);
        
        // Validate sequential numbering if applicable
        validateSequentialNumbering(journalEntries, result);
        
        return result;
    }
    
    /**
     * Checks for duplicate journal entries.
     */
    private void checkForDuplicates(List<JournalEntry> entries, ValidationResult result) {
        Map<String, List<JournalEntry>> duplicates = entries.stream()
                .filter(e -> e.getReferenceNumber() != null)
                .collect(Collectors.groupingBy(JournalEntry::getReferenceNumber));
        
        duplicates.forEach((refNum, list) -> {
            if (list.size() > 1) {
                result.addWarning(refNum, 
                    String.format("Duplicate reference number found: %d entries", list.size()));
            }
        });
    }
    
    /**
     * Validates sequential numbering of journal entries.
     */
    private void validateSequentialNumbering(List<JournalEntry> entries, ValidationResult result) {
        List<Long> sequenceNumbers = entries.stream()
                .map(JournalEntry::getSequenceNumber)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
        
        for (int i = 1; i < sequenceNumbers.size(); i++) {
            long current = sequenceNumbers.get(i);
            long previous = sequenceNumbers.get(i - 1);
            
            if (current - previous > 1) {
                result.addWarning(null, 
                    String.format("Gap in sequence numbers: %d to %d", previous, current));
            }
        }
    }
    
    /**
     * Result class for batch validation.
     */
    public static class ValidationResult {
        private final List<String> validEntries = new java.util.ArrayList<>();
        private final Map<String, String> errors = new java.util.HashMap<>();
        private final List<String> warnings = new java.util.ArrayList<>();
        
        public void addValid(String entryId) {
            validEntries.add(entryId);
        }
        
        public void addError(String entryId, String message) {
            errors.put(entryId, message);
        }
        
        public void addWarning(String entryId, String message) {
            warnings.add(entryId + ": " + message);
        }
        
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
        
        public int getValidCount() {
            return validEntries.size();
        }
        
        public int getErrorCount() {
            return errors.size();
        }
        
        public Map<String, String> getErrors() {
            return java.util.Collections.unmodifiableMap(errors);
        }
        
        public List<String> getWarnings() {
            return java.util.Collections.unmodifiableList(warnings);
        }
    }
}
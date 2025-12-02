package com.waqiti.accounting.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when journal entry debits don't equal credits
 */
public class JournalEntryNotBalancedException extends AccountingException {

    private final String entryId;
    private final BigDecimal totalDebits;
    private final BigDecimal totalCredits;
    private final BigDecimal difference;

    public JournalEntryNotBalancedException(String entryId, BigDecimal totalDebits, BigDecimal totalCredits) {
        super("JOURNAL_NOT_BALANCED",
            String.format("Journal entry %s not balanced: debits=%s, credits=%s, difference=%s",
                entryId, totalDebits, totalCredits, totalDebits.subtract(totalCredits)),
            entryId, totalDebits, totalCredits);
        this.entryId = entryId;
        this.totalDebits = totalDebits;
        this.totalCredits = totalCredits;
        this.difference = totalDebits.subtract(totalCredits);
    }

    public String getEntryId() {
        return entryId;
    }

    public BigDecimal getTotalDebits() {
        return totalDebits;
    }

    public BigDecimal getTotalCredits() {
        return totalCredits;
    }

    public BigDecimal getDifference() {
        return difference;
    }
}

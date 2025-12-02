package com.waqiti.transaction.domain;

/**
 * Ledger Entry Types following Double-Entry Bookkeeping
 *
 * @author Waqiti Platform Team
 */
public enum LedgerEntryType {
    /**
     * DEBIT - Increases assets or expenses, decreases liabilities or income
     */
    DEBIT,

    /**
     * CREDIT - Increases liabilities or income, decreases assets or expenses
     */
    CREDIT
}

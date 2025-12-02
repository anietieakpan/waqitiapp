package com.waqiti.accounting.exception;

/**
 * Exception thrown during account reconciliation errors
 */
public class ReconciliationException extends AccountingException {

    private final String accountCode;

    public ReconciliationException(String accountCode, String message) {
        super("RECONCILIATION_ERROR", message, accountCode);
        this.accountCode = accountCode;
    }

    public ReconciliationException(String accountCode, String message, Throwable cause) {
        super("RECONCILIATION_ERROR", message, cause, accountCode);
        this.accountCode = accountCode;
    }

    public String getAccountCode() {
        return accountCode;
    }
}

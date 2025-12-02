package com.waqiti.accounting.exception;

/**
 * Exception thrown when an account is not found in the chart of accounts
 */
public class AccountNotFoundException extends AccountingException {

    private final String accountCode;

    public AccountNotFoundException(String accountCode) {
        super("ACCOUNT_NOT_FOUND",
            String.format("Account not found: %s", accountCode),
            accountCode);
        this.accountCode = accountCode;
    }

    public String getAccountCode() {
        return accountCode;
    }
}

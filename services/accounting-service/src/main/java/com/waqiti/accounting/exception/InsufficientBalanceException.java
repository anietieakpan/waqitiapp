package com.waqiti.accounting.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when account balance is insufficient for an operation
 */
public class InsufficientBalanceException extends AccountingException {

    private final String accountCode;
    private final BigDecimal availableBalance;
    private final BigDecimal requiredAmount;

    public InsufficientBalanceException(String accountCode, BigDecimal availableBalance, BigDecimal requiredAmount) {
        super("INSUFFICIENT_BALANCE",
            String.format("Insufficient balance in account %s: available=%s, required=%s",
                accountCode, availableBalance, requiredAmount),
            accountCode, availableBalance, requiredAmount);
        this.accountCode = accountCode;
        this.availableBalance = availableBalance;
        this.requiredAmount = requiredAmount;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }
}

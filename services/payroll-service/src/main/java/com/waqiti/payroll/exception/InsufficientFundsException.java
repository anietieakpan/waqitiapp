package com.waqiti.payroll.exception;

import java.math.BigDecimal;

/**
 * Insufficient Funds Exception
 *
 * Thrown when company account has insufficient funds for payroll processing.
 * Non-retryable without funding - triggers immediate alert to company.
 */
public class InsufficientFundsException extends RuntimeException {

    private final String companyId;
    private final BigDecimal requiredAmount;
    private final BigDecimal availableAmount;
    private final BigDecimal shortfall;

    public InsufficientFundsException(String message) {
        super(message);
        this.companyId = null;
        this.requiredAmount = null;
        this.availableAmount = null;
        this.shortfall = null;
    }

    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
        this.companyId = null;
        this.requiredAmount = null;
        this.availableAmount = null;
        this.shortfall = null;
    }

    public InsufficientFundsException(String companyId, BigDecimal requiredAmount, BigDecimal availableAmount) {
        super(String.format("Insufficient funds for company %s: Required $%s, Available $%s, Shortfall $%s",
            companyId, requiredAmount, availableAmount, requiredAmount.subtract(availableAmount)));
        this.companyId = companyId;
        this.requiredAmount = requiredAmount;
        this.availableAmount = availableAmount;
        this.shortfall = requiredAmount.subtract(availableAmount);
    }

    public String getCompanyId() {
        return companyId;
    }

    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }

    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }

    public BigDecimal getShortfall() {
        return shortfall;
    }
}

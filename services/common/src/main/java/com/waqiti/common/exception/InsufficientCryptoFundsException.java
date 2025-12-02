package com.waqiti.common.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when cryptocurrency wallet has insufficient funds.
 * Similar to InsufficientFundsException but specific to crypto operations.
 *
 * Business logic should:
 * - Return user-friendly error message
 * - Suggest deposit or reduce amount
 * - Log for analytics
 * - DO NOT retry (business rule, not transient error)
 *
 * @author Waqiti Platform
 */
public class InsufficientCryptoFundsException extends RuntimeException {

    private final String walletAddress;
    private final String cryptocurrency;
    private final BigDecimal availableBalance;
    private final BigDecimal requiredAmount;
    private final BigDecimal shortfall;

    /**
     * Creates exception with message
     */
    public InsufficientCryptoFundsException(String message) {
        super(message);
        this.walletAddress = null;
        this.cryptocurrency = null;
        this.availableBalance = null;
        this.requiredAmount = null;
        this.shortfall = null;
    }

    /**
     * Creates exception with message and cause
     */
    public InsufficientCryptoFundsException(String message, Throwable cause) {
        super(message, cause);
        this.walletAddress = null;
        this.cryptocurrency = null;
        this.availableBalance = null;
        this.requiredAmount = null;
        this.shortfall = null;
    }

    /**
     * Creates exception with detailed balance information
     */
    public InsufficientCryptoFundsException(String cryptocurrency, BigDecimal availableBalance,
                                           BigDecimal requiredAmount, String walletAddress) {
        super(String.format("Insufficient %s balance. Available: %s, Required: %s, Shortfall: %s",
            cryptocurrency, availableBalance, requiredAmount,
            requiredAmount.subtract(availableBalance)));
        this.walletAddress = walletAddress;
        this.cryptocurrency = cryptocurrency;
        this.availableBalance = availableBalance;
        this.requiredAmount = requiredAmount;
        this.shortfall = requiredAmount.subtract(availableBalance);
    }

    public String getWalletAddress() {
        return walletAddress;
    }

    public String getCryptocurrency() {
        return cryptocurrency;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }

    public BigDecimal getShortfall() {
        return shortfall;
    }
}

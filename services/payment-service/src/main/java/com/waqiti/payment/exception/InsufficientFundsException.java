package com.waqiti.payment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Exception thrown when a wallet has insufficient funds for a transaction
 * Provides detailed information about the fund shortage
 */
@Getter
@ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
public class InsufficientFundsException extends WalletServiceException {
    
    private final String walletId;
    private final String userId;
    private final BigDecimal requestedAmount;
    private final BigDecimal availableBalance;
    private final BigDecimal shortfall;
    private final String currency;
    private final TransactionType transactionType;
    private final String transactionId;
    private final LocalDateTime occurredAt;
    private final Map<String, Object> additionalInfo;
    
    public enum TransactionType {
        PAYMENT,
        TRANSFER,
        WITHDRAWAL,
        PURCHASE,
        FEE,
        SUBSCRIPTION,
        RESERVATION,
        HOLD,
        SETTLEMENT,
        REFUND
    }
    
    /**
     * Basic constructor with minimal information
     */
    public InsufficientFundsException(String message) {
        super(message, "INSUFFICIENT_FUNDS", HttpStatus.PAYMENT_REQUIRED);
        this.walletId = null;
        this.userId = null;
        this.requestedAmount = BigDecimal.ZERO;
        this.availableBalance = BigDecimal.ZERO;
        this.shortfall = BigDecimal.ZERO;
        this.currency = "USD";
        this.transactionType = TransactionType.PAYMENT;
        this.transactionId = null;
        this.occurredAt = LocalDateTime.now();
        this.additionalInfo = new HashMap<>();
    }
    
    /**
     * Constructor with wallet and amount information
     */
    public InsufficientFundsException(String walletId, BigDecimal requestedAmount, BigDecimal availableBalance) {
        super(buildMessage(walletId, requestedAmount, availableBalance), 
              "INSUFFICIENT_FUNDS", 
              HttpStatus.PAYMENT_REQUIRED,
              walletId,
              ErrorCategory.INSUFFICIENT_FUNDS);
        
        this.walletId = walletId;
        this.userId = null;
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
        this.shortfall = requestedAmount.subtract(availableBalance);
        this.currency = "USD";
        this.transactionType = TransactionType.PAYMENT;
        this.transactionId = null;
        this.occurredAt = LocalDateTime.now();
        this.additionalInfo = new HashMap<>();
    }
    
    /**
     * Full constructor with all details
     */
    public InsufficientFundsException(
            String walletId,
            String userId,
            BigDecimal requestedAmount,
            BigDecimal availableBalance,
            String currency,
            TransactionType transactionType,
            String transactionId) {
        
        super(buildDetailedMessage(walletId, userId, requestedAmount, availableBalance, currency, transactionType),
              "INSUFFICIENT_FUNDS",
              HttpStatus.PAYMENT_REQUIRED,
              walletId,
              ErrorCategory.INSUFFICIENT_FUNDS,
              buildDetails(requestedAmount, availableBalance, currency, transactionType, transactionId));
        
        this.walletId = walletId;
        this.userId = userId;
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
        this.shortfall = requestedAmount.subtract(availableBalance);
        this.currency = currency;
        this.transactionType = transactionType;
        this.transactionId = transactionId;
        this.occurredAt = LocalDateTime.now();
        this.additionalInfo = new HashMap<>();
    }
    
    /**
     * Constructor with cause
     */
    public InsufficientFundsException(String message, Throwable cause) {
        super(message, "INSUFFICIENT_FUNDS", HttpStatus.PAYMENT_REQUIRED, null, ErrorCategory.INSUFFICIENT_FUNDS, null, cause);
        this.walletId = null;
        this.userId = null;
        this.requestedAmount = BigDecimal.ZERO;
        this.availableBalance = BigDecimal.ZERO;
        this.shortfall = BigDecimal.ZERO;
        this.currency = "USD";
        this.transactionType = TransactionType.PAYMENT;
        this.transactionId = null;
        this.occurredAt = LocalDateTime.now();
        this.additionalInfo = new HashMap<>();
    }
    
    /**
     * Add additional information to the exception
     */
    public InsufficientFundsException withAdditionalInfo(String key, Object value) {
        this.additionalInfo.put(key, value);
        super.withDetail(key, value);
        return this;
    }
    
    /**
     * Add overdraft information if applicable
     */
    public InsufficientFundsException withOverdraftInfo(BigDecimal overdraftLimit, BigDecimal currentOverdraft) {
        this.additionalInfo.put("overdraftLimit", overdraftLimit);
        this.additionalInfo.put("currentOverdraft", currentOverdraft);
        this.additionalInfo.put("overdraftAvailable", overdraftLimit.subtract(currentOverdraft));
        return this;
    }
    
    /**
     * Add pending transaction information
     */
    public InsufficientFundsException withPendingTransactions(BigDecimal pendingAmount, int pendingCount) {
        this.additionalInfo.put("pendingAmount", pendingAmount);
        this.additionalInfo.put("pendingTransactionCount", pendingCount);
        this.additionalInfo.put("effectiveBalance", availableBalance.subtract(pendingAmount));
        return this;
    }
    
    /**
     * Add hold information
     */
    public InsufficientFundsException withHoldInfo(BigDecimal totalHolds, int holdCount) {
        this.additionalInfo.put("totalHolds", totalHolds);
        this.additionalInfo.put("holdCount", holdCount);
        this.additionalInfo.put("balanceAfterHolds", availableBalance.subtract(totalHolds));
        return this;
    }
    
    /**
     * Add suggested actions
     */
    public InsufficientFundsException withSuggestedActions(String... actions) {
        this.additionalInfo.put("suggestedActions", actions);
        return this;
    }
    
    /**
     * Build basic error message
     */
    private static String buildMessage(String walletId, BigDecimal requestedAmount, BigDecimal availableBalance) {
        return String.format(
            "Insufficient funds in wallet %s. Requested: %s, Available: %s, Shortfall: %s",
            walletId,
            requestedAmount.toPlainString(),
            availableBalance.toPlainString(),
            requestedAmount.subtract(availableBalance).toPlainString()
        );
    }
    
    /**
     * Build detailed error message
     */
    private static String buildDetailedMessage(
            String walletId,
            String userId,
            BigDecimal requestedAmount,
            BigDecimal availableBalance,
            String currency,
            TransactionType transactionType) {
        
        return String.format(
            "Insufficient funds for %s transaction. User: %s, Wallet: %s, Requested: %s %s, Available: %s %s, Shortfall: %s %s",
            transactionType.name().toLowerCase(),
            userId != null ? userId : "N/A",
            walletId,
            requestedAmount.toPlainString(),
            currency,
            availableBalance.toPlainString(),
            currency,
            requestedAmount.subtract(availableBalance).toPlainString(),
            currency
        );
    }
    
    /**
     * Build details map for parent exception
     */
    private static Map<String, Object> buildDetails(
            BigDecimal requestedAmount,
            BigDecimal availableBalance,
            String currency,
            TransactionType transactionType,
            String transactionId) {
        
        Map<String, Object> details = new HashMap<>();
        details.put("requestedAmount", requestedAmount);
        details.put("availableBalance", availableBalance);
        details.put("shortfall", requestedAmount.subtract(availableBalance));
        details.put("currency", currency);
        details.put("transactionType", transactionType.name());
        
        if (transactionId != null) {
            details.put("transactionId", transactionId);
        }
        
        return details;
    }
    
    /**
     * Get shortfall percentage
     */
    public BigDecimal getShortfallPercentage() {
        if (requestedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return shortfall.multiply(BigDecimal.valueOf(100))
                       .divide(requestedAmount, 2, RoundingMode.HALF_UP);
    }
    
    /**
     * Check if overdraft would cover the shortfall
     */
    public boolean canBeCoveredByOverdraft(BigDecimal overdraftLimit) {
        return shortfall.compareTo(overdraftLimit) <= 0;
    }
    
    /**
     * Get suggested top-up amount with buffer
     */
    public BigDecimal getSuggestedTopUpAmount(BigDecimal bufferPercentage) {
        BigDecimal buffer = requestedAmount.multiply(bufferPercentage.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP));
        return shortfall.add(buffer);
    }
    
    /**
     * Convert to detailed error response
     */
    @Override
    public Map<String, Object> toErrorResponse() {
        Map<String, Object> response = super.toErrorResponse();
        
        // Add specific insufficient funds details
        Map<String, Object> fundDetails = new HashMap<>();
        fundDetails.put("walletId", walletId);
        fundDetails.put("userId", userId);
        fundDetails.put("requestedAmount", requestedAmount);
        fundDetails.put("availableBalance", availableBalance);
        fundDetails.put("shortfall", shortfall);
        fundDetails.put("shortfallPercentage", getShortfallPercentage());
        fundDetails.put("currency", currency);
        fundDetails.put("transactionType", transactionType);
        fundDetails.put("transactionId", transactionId);
        fundDetails.put("occurredAt", occurredAt);
        
        if (!additionalInfo.isEmpty()) {
            fundDetails.put("additionalInfo", additionalInfo);
        }
        
        response.put("fundDetails", fundDetails);
        
        return response;
    }
    
    @Override
    public String toString() {
        return String.format(
            "InsufficientFundsException{walletId='%s', userId='%s', requestedAmount=%s, availableBalance=%s, shortfall=%s, currency='%s', transactionType=%s, transactionId='%s'}",
            walletId, userId, requestedAmount, availableBalance, shortfall, currency, transactionType, transactionId
        );
    }
}
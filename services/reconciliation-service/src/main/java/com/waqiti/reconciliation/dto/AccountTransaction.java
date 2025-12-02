package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountTransaction {

    private UUID transactionId;
    
    private UUID accountId;
    
    private String transactionType;
    
    private String transactionCode;
    
    private BigDecimal amount;
    
    private String currency;
    
    private TransactionDirection direction;
    
    private String description;
    
    private String reference;
    
    private String externalReference;
    
    private LocalDateTime transactionDate;
    
    private LocalDateTime valueDate;
    
    private LocalDateTime postingDate;
    
    private TransactionStatus status;
    
    private String channel;
    
    private BigDecimal runningBalance;
    
    private String reversalReference;
    
    private UUID originalTransactionId;
    
    private Map<String, String> additionalInfo;

    public enum TransactionDirection {
        DEBIT,
        CREDIT
    }

    public enum TransactionStatus {
        PENDING,
        POSTED,
        CLEARED,
        REVERSED,
        FAILED,
        CANCELLED
    }

    public boolean isDebit() {
        return TransactionDirection.DEBIT.equals(direction);
    }

    public boolean isCredit() {
        return TransactionDirection.CREDIT.equals(direction);
    }

    public boolean isPosted() {
        return TransactionStatus.POSTED.equals(status) || 
               TransactionStatus.CLEARED.equals(status);
    }

    public boolean isPending() {
        return TransactionStatus.PENDING.equals(status);
    }

    public boolean isReversed() {
        return TransactionStatus.REVERSED.equals(status);
    }

    public BigDecimal getSignedAmount() {
        if (amount == null) return BigDecimal.ZERO;
        return isDebit() ? amount.negate() : amount;
    }

    public boolean isReversal() {
        return originalTransactionId != null;
    }
}
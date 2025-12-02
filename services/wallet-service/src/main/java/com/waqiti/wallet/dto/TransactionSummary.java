package com.waqiti.wallet.dto;

import com.waqiti.wallet.domain.TransactionStatus;
import com.waqiti.wallet.domain.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction summary data transfer object
 * Contains basic transaction information for caching
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummary {
    
    private UUID transactionId;
    private UUID walletId;
    private TransactionType type;
    private BigDecimal amount;
    private String currency;
    private String description;
    private TransactionStatus status;
    private LocalDateTime createdAt;
    private String reference;
    private String category;
    private UUID relatedWalletId; // For transfers
    
    /**
     * Check if transaction is completed
     */
    public boolean isCompleted() {
        return status == TransactionStatus.COMPLETED;
    }
    
    /**
     * Check if transaction is pending
     */
    public boolean isPending() {
        return status == TransactionStatus.PENDING || 
               status == TransactionStatus.PROCESSING;
    }
    
    /**
     * Check if transaction failed
     */
    public boolean isFailed() {
        return status == TransactionStatus.FAILED || 
               status == TransactionStatus.CANCELLED;
    }
    
    /**
     * Get display amount (positive for credits, negative for debits)
     */
    public BigDecimal getDisplayAmount() {
        if (type == TransactionType.DEBIT) {
            return amount.negate();
        }
        return amount;
    }
    
    /**
     * Get simple description for UI
     */
    public String getSimpleDescription() {
        if (description != null && !description.trim().isEmpty()) {
            return description;
        }
        
        switch (type) {
            case CREDIT:
                return "Incoming transfer";
            case DEBIT:
                return "Outgoing transfer";
            default:
                return "Transaction";
        }
    }
}
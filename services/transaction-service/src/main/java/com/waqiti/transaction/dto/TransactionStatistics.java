package com.waqiti.transaction.dto;

import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.domain.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for transaction statistics to avoid loading full entities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatistics {
    private TransactionType type;
    private TransactionStatus status;
    private Long count;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    
    // Constructor for JPQL projection
    public TransactionStatistics(TransactionType type, TransactionStatus status, 
                               Long count, BigDecimal totalAmount, Double averageAmount,
                               BigDecimal minAmount, BigDecimal maxAmount) {
        this.type = type;
        this.status = status;
        this.count = count;
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        this.averageAmount = averageAmount != null ? BigDecimal.valueOf(averageAmount) : BigDecimal.ZERO;
        this.minAmount = minAmount != null ? minAmount : BigDecimal.ZERO;
        this.maxAmount = maxAmount != null ? maxAmount : BigDecimal.ZERO;
    }
}
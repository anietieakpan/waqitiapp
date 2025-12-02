package com.waqiti.transaction.dto;

import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.domain.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummaryResponse {
    
    private String walletId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer totalTransactions;
    private BigDecimal totalVolume;
    private BigDecimal totalFees;
    private BigDecimal averageTransactionAmount;
    private Map<TransactionType, Integer> transactionsByType;
    private Map<TransactionType, BigDecimal> volumeByType;
    private Map<TransactionStatus, Integer> transactionsByStatus;
}
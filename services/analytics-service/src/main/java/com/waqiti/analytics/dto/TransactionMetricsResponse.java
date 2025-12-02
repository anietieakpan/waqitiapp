package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for transaction metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMetricsResponse {
    
    private String period;
    private Long transactionCount;
    private BigDecimal totalVolume;
    private BigDecimal averageAmount;
    private BigDecimal maxAmount;
    private BigDecimal minAmount;
    private Long successfulTransactions;
    private Long failedTransactions;
    private Double successRate;
    private Instant lastUpdated;
}
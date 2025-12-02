package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Real-time transaction statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStats {
    
    private long totalTransactions;
    private BigDecimal totalVolume;
    private long successfulTransactions;
    private long failedTransactions;
    private BigDecimal averageAmount;
    private Instant timestamp;
    private String period;
}
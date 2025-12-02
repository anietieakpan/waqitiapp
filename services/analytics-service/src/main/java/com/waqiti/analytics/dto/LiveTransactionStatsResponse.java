package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for live transaction statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveTransactionStatsResponse {
    
    private Long totalTransactions;
    private BigDecimal totalVolume;
    private BigDecimal averageAmount;
    private Long successfulTransactions;
    private Long failedTransactions;
    private Double successRate;
    private Instant timestamp;
    private String period;
    
    /**
     * Calculate success rate
     */
    public Double getSuccessRate() {
        if (successRate == null && totalTransactions != null && totalTransactions > 0) {
            successRate = (double) successfulTransactions / totalTransactions * 100;
        }
        return successRate;
    }
}
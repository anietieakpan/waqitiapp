package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment Completion Metrics DTO
 *
 * Represents aggregated payment completion metrics for a time period.
 *
 * Used by:
 * - AnalyticsService.getPaymentMetrics()
 * - Dashboard APIs
 * - Reporting services
 *
 * @author Waqiti Platform Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-10
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCompletionMetrics {

    /**
     * Total number of transactions in period
     */
    private Long totalTransactions;

    /**
     * Total transaction volume (sum of all transaction amounts)
     */
    private BigDecimal totalVolume;

    /**
     * Average transaction value
     */
    private BigDecimal averageTransactionValue;

    /**
     * Success rate (percentage of successful transactions)
     */
    private BigDecimal successRate;

    /**
     * Number of successful transactions
     */
    private Long successfulTransactions;

    /**
     * Number of failed transactions
     */
    private Long failedTransactions;

    /**
     * Number of pending transactions
     */
    private Long pendingTransactions;

    /**
     * Total fees collected
     */
    private BigDecimal totalFees;

    /**
     * Average processing time in milliseconds
     */
    private Long averageProcessingTimeMs;

    /**
     * Peak transaction hour (0-23)
     */
    private Integer peakHour;

    /**
     * Currency code
     */
    private String currency;

    /**
     * Period start date
     */
    private LocalDateTime periodStart;

    /**
     * Period end date
     */
    private LocalDateTime periodEnd;

    /**
     * Metrics calculation timestamp
     */
    @Builder.Default
    private LocalDateTime calculatedAt = LocalDateTime.now();
}

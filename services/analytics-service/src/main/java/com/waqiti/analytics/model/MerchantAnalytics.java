package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Comprehensive merchant analytics model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAnalytics {
    
    private String merchantId;
    private String merchantName;
    
    // Transaction metrics
    private Long totalTransactions;
    private BigDecimal totalRevenue;
    private BigDecimal averageTransactionAmount;
    private BigDecimal maxTransactionAmount;
    
    // Customer metrics
    private Long uniqueCustomers;
    private BigDecimal averageTransactionsPerCustomer;
    private BigDecimal customerLifetimeValue;
    private BigDecimal repeatCustomerRate;
    
    // Performance metrics
    private BigDecimal successRate;
    private BigDecimal revenueGrowthRate;
    private BigDecimal churnRate;
    
    // Breakdowns
    private Map<String, BigDecimal> paymentMethodBreakdown;
    private Map<String, BigDecimal> currencyBreakdown;
    private Map<String, Long> statusCounts;
    
    // Time-based metrics
    private Instant firstTransactionDate;
    private Instant lastTransactionDate;
    private Integer peakHour;
    
    // Risk and fraud
    private BigDecimal fraudRate;
    private BigDecimal disputeRate;
    private String riskLevel;
    
    // Metadata
    private Instant createdAt;
    private Instant lastUpdated;
    private String category;
    private String region;
}
package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Response DTO for merchant analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAnalyticsResponse {
    
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
    
    // Time-based metrics
    private Integer peakHour;
    
    // Risk metrics
    private BigDecimal fraudRate;
    private BigDecimal disputeRate;
    private String riskLevel;
}
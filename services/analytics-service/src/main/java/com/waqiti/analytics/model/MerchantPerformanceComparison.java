package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Merchant performance comparison against industry averages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantPerformanceComparison {
    
    private String merchantId;
    private Integer revenuePercentile;
    private Integer transactionPercentile;
    private Double industryAverageRevenue;
    private Double industryAverageTransactions;
    private Double industryAverageSuccessRate;
}
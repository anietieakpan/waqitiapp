package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Result for merchant ranking analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRankingResult {
    
    private String merchantId;
    private String merchantName;
    private BigDecimal totalRevenue;
    private Long totalTransactions;
    private Long uniqueCustomers;
    private BigDecimal successRate;
    private Integer ranking;
}
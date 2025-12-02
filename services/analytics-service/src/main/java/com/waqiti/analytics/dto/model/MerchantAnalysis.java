package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Merchant analysis model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAnalysis {
    private Map<String, BigDecimal> topMerchants;
    private Map<String, Integer> merchantFrequency;
    private List<MerchantInsight> merchantInsights;
    private String favoriteTimeOfDay;
    private String favoriteCategory;
    private BigDecimal averageTransactionAmount;
    private List<String> newMerchants;
    private List<String> recurringMerchants;
}
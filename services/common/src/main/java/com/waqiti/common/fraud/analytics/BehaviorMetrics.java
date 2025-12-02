package com.waqiti.common.fraud.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map; /**
 * Behavior metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorMetrics {
    private double averageAmount;
    private double amountVariability;
    private double transactionFrequency;
    private int uniqueMerchantCount;
    private Map<Object, Long> channelDistribution;
    private Map<String, Long> timePatterns;
    
    public static BehaviorMetrics empty() {
        return BehaviorMetrics.builder().build();
    }
}

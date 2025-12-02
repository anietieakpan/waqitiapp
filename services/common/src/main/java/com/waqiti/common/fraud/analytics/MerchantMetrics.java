package com.waqiti.common.fraud.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map; /**
 * Merchant metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantMetrics {
    private double averageTransactionAmount;
    private int totalTransactions;
    private double chargebackRate;
    private double approvalRate;
    private Map<String, Object> additionalMetrics;
}

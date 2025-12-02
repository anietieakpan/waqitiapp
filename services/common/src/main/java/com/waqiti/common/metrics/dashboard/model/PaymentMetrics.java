package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Payment metrics data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMetrics {
    private Long totalPayments;
    private BigDecimal totalVolume;
    private BigDecimal avgPaymentAmount;
    private Double successRate;
    private Map<String, Long> paymentsByMethod;
    private Map<String, BigDecimal> volumeByMethod;
    private List<String> topMerchants;
}
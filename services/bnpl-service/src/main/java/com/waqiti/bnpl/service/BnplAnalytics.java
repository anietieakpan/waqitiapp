package com.waqiti.bnpl.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * BNPL Analytics data structure
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnplAnalytics {
    
    private String userId;
    private Long totalBnplPayments;
    private Long successfulPayments;
    private Long failedPayments;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private Double successRate;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    
    // BNPL-specific metrics
    private Long activePlans;
    private Long completedPlans;
    private Long overdueInstallments;
    private BigDecimal totalOutstanding;
    private BigDecimal totalLateFees;
    private Integer averageInstallmentCount;
    private Double averageCompletionRate;
    
    // Time series data
    private List<PaymentTrend> paymentTrends;
    private List<PlanStatistics> planStatistics;
    private Map<String, Object> riskMetrics;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentTrend {
        private LocalDateTime period;
        private Long paymentCount;
        private BigDecimal totalAmount;
        private Double successRate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanStatistics {
        private String merchantName;
        private Long planCount;
        private BigDecimal totalFinanced;
        private Double averageInterestRate;
        private Integer averageInstallments;
    }
}
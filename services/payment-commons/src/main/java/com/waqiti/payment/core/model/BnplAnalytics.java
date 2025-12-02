package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Buy Now Pay Later (BNPL) analytics model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnplAnalytics {
    
    private String userId;
    private long totalBnplPayments;
    private long successfulPayments;
    private long failedPayments;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private double successRate;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    
    // BNPL-specific metrics
    private long activeBnplPlans;
    private long completedBnplPlans;
    private long suspendedBnplPlans;
    private BigDecimal totalOutstandingAmount;
    private BigDecimal averageInstallmentAmount;
    private double averageInstallments;
    private BigDecimal totalInterestPaid;
    private double averageInterestRate;
    private double onTimePaymentRate;
    
    public double getFailureRate() {
        if (totalBnplPayments == 0) return 0.0;
        return (double) failedPayments / totalBnplPayments * 100.0;
    }
    
    public double getPlanCompletionRate() {
        long totalPlans = activeBnplPlans + completedBnplPlans + suspendedBnplPlans;
        if (totalPlans == 0) return 0.0;
        return (double) completedBnplPlans / totalPlans * 100.0;
    }
    
    public double getDefaultRate() {
        long totalPlans = activeBnplPlans + completedBnplPlans + suspendedBnplPlans;
        if (totalPlans == 0) return 0.0;
        return (double) suspendedBnplPlans / totalPlans * 100.0;
    }
    
    public BigDecimal getAverageInterestPerPlan() {
        if (completedBnplPlans == 0) return BigDecimal.ZERO;
        return totalInterestPaid.divide(new BigDecimal(completedBnplPlans), 2, RoundingMode.HALF_UP);
    }
}
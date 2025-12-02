package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Recurring payment analytics model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringPaymentAnalytics {
    
    private String userId;
    private long totalRecurringPayments;
    private long activeRecurringPayments;
    private long pausedRecurringPayments;
    private long completedRecurringPayments;
    private long cancelledRecurringPayments;
    private long successfulExecutions;
    private long failedExecutions;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private double successRate;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    
    // Recurring-specific metrics
    private double averageExecutionsPerPayment;
    private long totalExecutions;
    private BigDecimal projectedMonthlyAmount;
    private double reliabilityScore;
    
    public double getFailureRate() {
        if (totalExecutions == 0) return 0.0;
        return (double) failedExecutions / totalExecutions * 100.0;
    }
    
    public double getActiveRate() {
        if (totalRecurringPayments == 0) return 0.0;
        return (double) activeRecurringPayments / totalRecurringPayments * 100.0;
    }
    
    public double getCompletionRate() {
        if (totalRecurringPayments == 0) return 0.0;
        return (double) completedRecurringPayments / totalRecurringPayments * 100.0;
    }
}
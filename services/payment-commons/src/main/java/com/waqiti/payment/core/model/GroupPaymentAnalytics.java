package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Group payment analytics model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPaymentAnalytics {
    
    private String userId;
    private long totalGroupPayments;
    private long successfulGroupPayments;
    private long failedGroupPayments;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private double successRate;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    
    // Group-specific metrics
    private long activeGroupPayments;
    private long completedGroupPayments;
    private long cancelledGroupPayments;
    private double averageParticipants;
    private BigDecimal averageGroupSize;
    
    public double getFailureRate() {
        if (totalGroupPayments == 0) return 0.0;
        return (double) failedGroupPayments / totalGroupPayments * 100.0;
    }
    
    public double getCompletionRate() {
        if (totalGroupPayments == 0) return 0.0;
        return (double) completedGroupPayments / totalGroupPayments * 100.0;
    }
}
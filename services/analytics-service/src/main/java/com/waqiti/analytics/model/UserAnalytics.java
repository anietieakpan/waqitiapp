package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * User analytics model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAnalytics {
    
    private String userId;
    private Long totalTransactions;
    private BigDecimal totalSpent;
    private BigDecimal averageTransactionAmount;
    private BigDecimal maxTransactionAmount;
    private Long totalSessions;
    private Instant firstTransactionDate;
    private Instant lastTransactionDate;
    private Instant lastActiveDate;
    private Instant createdAt;
    private Instant lastUpdated;
    
    // Velocity metrics
    private Long dailyVelocity;
    private Long weeklyVelocity;
    
    // Preferences
    private Map<String, Long> preferredPaymentMethods;
    private Map<String, Long> merchantPreferences;
    private Map<String, Long> pageViews;
    private Map<String, Long> deviceUsage;
    
    // Behavioral metrics
    private Integer peakSpendingHour;
    private Integer loyaltyScore;
    private String segment;
}
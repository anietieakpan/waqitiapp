package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Response DTO for user analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAnalyticsResponse {
    
    private String userId;
    private Long totalTransactions;
    private BigDecimal totalSpent;
    private BigDecimal averageTransactionAmount;
    private BigDecimal maxTransactionAmount;
    private Long totalSessions;
    
    // Date information
    private Instant firstTransactionDate;
    private Instant lastTransactionDate;
    private Instant lastActiveDate;
    
    // Velocity metrics
    private Long dailyVelocity;
    private Long weeklyVelocity;
    
    // Preferences and patterns
    private Map<String, Long> preferredPaymentMethods;
    private Map<String, Long> merchantPreferences;
    private Integer peakSpendingHour;
    
    // Scoring and segmentation
    private Integer loyaltyScore;
    private String segment;
    
    // Risk indicators
    private String riskLevel;
    private BigDecimal riskScore;
}
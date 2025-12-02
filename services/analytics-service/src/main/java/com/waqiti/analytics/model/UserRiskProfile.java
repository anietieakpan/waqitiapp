package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * User risk profile for fraud analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRiskProfile {
    
    private String userId;
    private BigDecimal riskScore;
    private String riskLevel;
    private Long transactionCount;
    private Long flaggedTransactions;
    private Instant createdAt;
    private Instant lastUpdated;
}
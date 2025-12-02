package com.waqiti.recurringpayment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Risk Profile DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskProfile {
    private String userId;
    private double overallRiskScore;
    private String riskCategory;
    private List<String> riskFlags;
    private Map<String, Double> riskScores;
    private int successfulTransactions;
    private int failedTransactions;
    private int disputedTransactions;
    private Instant lastUpdated;
    private Instant memberSince;
    private boolean verified;
    private List<String> verificationMethods;
}
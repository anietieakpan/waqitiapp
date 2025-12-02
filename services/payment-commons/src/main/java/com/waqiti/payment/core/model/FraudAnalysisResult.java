package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fraud analysis result model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAnalysisResult {
    
    private UUID paymentId;
    private int riskScore;
    private FraudRiskLevel riskLevel;
    private String riskFactors;
    private FraudRecommendation recommendation;
    private LocalDateTime analyzedAt;
    private String additionalNotes;
    
    public boolean isHighRisk() {
        return riskLevel == FraudRiskLevel.HIGH;
    }
    
    public boolean shouldBlock() {
        return recommendation == FraudRecommendation.BLOCK;
    }
    
    public boolean requiresReview() {
        return recommendation == FraudRecommendation.REVIEW;
    }
}
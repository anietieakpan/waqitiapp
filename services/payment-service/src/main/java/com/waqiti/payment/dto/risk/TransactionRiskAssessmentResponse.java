/**
 * Transaction Risk Assessment Response DTO
 * Contains the results of a transaction risk assessment
 */
package com.waqiti.payment.dto.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRiskAssessmentResponse {
    
    /**
     * Transaction ID that was assessed
     */
    private String transactionId;
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Overall risk score (0.0 to 1.0)
     */
    private Double riskScore;
    
    /**
     * Risk level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String riskLevel;
    
    /**
     * Assessment result (APPROVE, DECLINE, REVIEW, BLOCK)
     */
    private String assessmentResult;
    
    /**
     * Confidence level of the assessment (0.0 to 1.0)
     */
    private Double confidenceLevel;
    
    /**
     * Fraud probability (0.0 to 1.0)
     */
    private Double fraudProbability;
    
    /**
     * Individual risk component scores
     */
    private Map<String, Double> riskComponents;
    
    /**
     * Detected risk factors
     */
    private List<String> riskFactors;
    
    /**
     * Fraud indicators found
     */
    private List<String> fraudIndicators;
    
    /**
     * Recommended actions
     */
    private List<String> recommendedActions;
    
    /**
     * Reason for the assessment result
     */
    private String assessmentReason;
    
    /**
     * Rules that were triggered
     */
    private List<String> triggeredRules;
    
    /**
     * Whether enhanced monitoring is recommended
     */
    private Boolean enhancedMonitoringRecommended;
    
    /**
     * Whether manual review is required
     */
    private Boolean manualReviewRequired;
    
    /**
     * Assessment model version used
     */
    private String modelVersion;
    
    /**
     * Assessment processing time in milliseconds
     */
    private Long processedTimeMs;
    
    /**
     * When the assessment was completed
     */
    private Instant assessedAt;
    
    /**
     * Expiry time for this assessment
     */
    private Instant expiresAt;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
}
package com.waqiti.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for BNPL fraud analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnplFraudAnalysisResponse {
    
    // Application identifiers
    private UUID applicationId;
    private UUID userId;
    private String merchantId;
    private BigDecimal purchaseAmount;
    private BigDecimal financedAmount;
    
    // Core fraud detection scores (from existing system)
    private double coreRiskScore;
    private double mlScore;
    private double velocityScore;
    private double behavioralScore;
    private double geolocationScore;
    private double deviceScore;
    private double patternScore;
    
    // BNPL-specific risk scores
    private double bnplVelocityScore;
    private double creditCorrelationScore;
    private double merchantRiskScore;  
    private double amountPatternScore;
    private double applicationPatternScore;
    
    // Final assessment
    private double finalRiskScore;
    private String riskLevel; // CRITICAL, HIGH, MEDIUM, LOW, MINIMAL
    private String recommendedAction; // BLOCK_APPLICATION, MANUAL_REVIEW_REQUIRED, ENHANCED_VERIFICATION, MONITOR_CLOSELY, APPROVE
    private double confidenceLevel;
    
    // Detailed analysis
    private List<String> riskFactors;
    private List<String> recommendations;
    private String analysisDetails;
    
    // Metadata
    private LocalDateTime timestamp;
    private String analysisVersion;
    private Long processingTimeMs;
    
    // Decision support
    private boolean requiresManualReview;
    private boolean requiresEnhancedVerification;
    private String approvalConditions;
    private BigDecimal suggestedCreditLimit;
    private Integer suggestedInstallments;
}
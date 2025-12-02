package com.waqiti.common.fraud.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive result of transaction analysis containing all analysis components
 * and risk assessments for fraud detection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAnalysisResult {
    
    /**
     * Transaction identifier
     */
    private String transactionId;
    
    /**
     * User identifier
     */
    private String userId;
    
    /**
     * Velocity analysis results
     */
    private VelocityAnalysis velocityAnalysis;
    
    /**
     * Pattern analysis results
     */
    private PatternAnalysis patternAnalysis;
    
    /**
     * Anomaly detection results
     */
    private AnomalyAnalysis anomalyAnalysis;
    
    /**
     * Overall risk analysis
     */
    private RiskAnalysis riskAnalysis;
    
    /**
     * Generated insights
     */
    private List<String> insights;
    
    /**
     * Recommended actions
     */
    private List<String> recommendations;
    
    /**
     * Analysis timestamp
     */
    @Builder.Default
    private LocalDateTime analysisTimestamp = LocalDateTime.now();
    
    /**
     * Processing time in milliseconds
     */
    private long processingTimeMs;
    
    /**
     * Error message if analysis failed
     */
    private String error;
    
    /**
     * Additional analysis metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Check if analysis was successful
     */
    public boolean isSuccessful() {
        return error == null;
    }
    
    /**
     * Get overall risk score
     */
    public double getOverallRiskScore() {
        return riskAnalysis != null ? riskAnalysis.getOverallRiskScore() : 0.0;
    }
    
    /**
     * Get risk level
     */
    public TransactionAnalysisService.RiskLevel getRiskLevel() {
        return riskAnalysis != null ? riskAnalysis.getRiskLevel() : TransactionAnalysisService.RiskLevel.MINIMAL;
    }
    
    /**
     * Check if high risk
     */
    public boolean isHighRisk() {
        TransactionAnalysisService.RiskLevel level = getRiskLevel();
        return level == TransactionAnalysisService.RiskLevel.HIGH || 
               level == TransactionAnalysisService.RiskLevel.CRITICAL;
    }
    
    /**
     * Get analysis summary
     */
    public String getSummary() {
        if (!isSuccessful()) {
            return String.format("Analysis failed for transaction %s: %s", transactionId, error);
        }
        
        return String.format("Transaction %s: Risk Level %s (Score: %.3f), Processing: %dms",
            transactionId, getRiskLevel(), getOverallRiskScore(), processingTimeMs);
    }
}


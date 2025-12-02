package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Fraud pattern result
 */
@Data
@Builder
@Jacksonized
public class FraudPatternResult {
    private String userId;
    private String transactionId;
    private boolean patternDetected;
    private String patternType;
    private double confidence;
    private List<String> matchingTransactions;
    private List<FraudPattern> detectedPatterns;
    private List<FraudPattern> staticPatterns;
    private List<FraudPattern> behavioralPatterns;
    private List<FraudPattern> networkPatterns;
    private List<FraudPattern> transactionPatterns;
    private List<FraudPattern> mlPatterns;
    private PatternRiskScore patternRiskScore;
    private List<PatternMitigationAction> recommendations;
    private LocalDateTime detectedAt;
    private int totalPatternsFound;
    private int highRiskPatterns;
    private String errorMessage;
    private Map<String, Object> patternDetails;
    private double riskScore;
    private String recommendation;
    private Instant analysisTimestamp;

    /**
     * Get detected patterns list
     */
    public List<FraudPattern> detectedPatterns() {
        return detectedPatterns != null ? detectedPatterns : new java.util.ArrayList<>();
    }
}
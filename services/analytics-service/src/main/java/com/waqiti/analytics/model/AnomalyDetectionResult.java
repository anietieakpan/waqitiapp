package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of anomaly detection analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyDetectionResult {
    
    private String anomalyId;
    private String type;
    private String entityId;
    private String entityType;
    
    // Anomaly details
    private String description;
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL
    private BigDecimal riskScore;
    private BigDecimal confidence;
    
    // Detection information
    private Instant detectedAt;
    private String detectionMethod;
    private String modelVersion;
    
    // Context information
    private Map<String, Object> context;
    private List<String> indicators;
    private List<String> patterns;
    
    // Impact assessment
    private String impactLevel;
    private BigDecimal potentialLoss;
    private Long affectedTransactions;
    
    // Resolution status
    private String status; // DETECTED, INVESTIGATING, RESOLVED, FALSE_POSITIVE
    private String assignedTo;
    private Instant resolvedAt;
    private String resolution;
    
    // Recommendation
    private String recommendedAction;
    private List<String> mitigationSteps;
    
    /**
     * Check if anomaly is critical
     */
    public boolean isCritical() {
        return "CRITICAL".equals(severity) || "HIGH".equals(severity);
    }
    
    /**
     * Check if anomaly requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return isCritical() && "DETECTED".equals(status);
    }
}
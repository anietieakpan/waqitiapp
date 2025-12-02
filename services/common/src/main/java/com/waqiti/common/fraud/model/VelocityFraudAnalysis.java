package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Velocity fraud analysis
 */
@Data
@Builder
@Jacksonized
public class VelocityFraudAnalysis {
    private String userId;
    private double velocityScore;
    private double threshold;
    private int transactionCount;
    private double totalAmount;
    private int timeWindow;
    private List<String> anomalies;
    private boolean isHighVelocity;
    private Instant analysisTimestamp;
    
    // Legacy fields for backward compatibility
    private boolean thresholdExceeded;
    private int transactionCount24h;
    private BigDecimal transactionVolume24h;
    private int transactionCount7d;
    private BigDecimal transactionVolume7d;
    private List<String> velocityViolations;
    private Map<String, Object> velocityMetrics;

    /**
     * Get risk score based on velocity
     */
    public double getRiskScore() {
        return velocityScore;
    }
}
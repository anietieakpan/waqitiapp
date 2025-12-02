package com.waqiti.common.metrics;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Fraud detection metrics data model
 */
@Data
@Builder
public class FraudMetrics {
    private String transactionId;
    private String userId;
    private String result; // APPROVED, BLOCKED, FLAGGED, MANUAL_REVIEW
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private double riskScore;
    private Duration processingTime;
    private String detectionEngine; // RULES_ENGINE, ML_MODEL, BLACKLIST
    private String blockedReason;
    private String[] triggeredRules;
    private boolean falsePositive;
}
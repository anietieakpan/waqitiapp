package com.waqiti.compliance.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction Anomaly DTO
 *
 * Represents an anomalous transaction detected by ML/statistical analysis.
 *
 * @author Waqiti Platform Engineering
 * @version 1.0.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAnomaly {

    // Anomaly Identification
    private String anomalyId;
    private Long transactionId;
    private Long customerId;

    // Anomaly Type
    private String anomalyType; // "AMOUNT", "FREQUENCY", "VELOCITY", "DESTINATION", "TIME", "GEOGRAPHIC"
    private String anomalyCategory; // "BEHAVIORAL", "STATISTICAL", "PATTERN_BASED", "RULE_BASED"

    // Description
    private String description;
    private String detailedExplanation;

    // Severity
    private String severity; // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    private Integer severityScore; // 0-100

    // Anomaly Details
    private BigDecimal transactionAmount;
    private BigDecimal expectedAmount;
    private BigDecimal deviationAmount;
    private BigDecimal deviationPercentage;

    // Statistical Measures
    private BigDecimal zScore; // Standard deviations from mean
    private BigDecimal anomalyScore; // 0.0-1.0 (higher = more anomalous)
    private BigDecimal probabilityScore; // Probability of being legitimate (0.0-1.0)

    // Context
    private String comparisonBasis; // "USER_HISTORY", "PEER_GROUP", "TIME_OF_DAY", "DAY_OF_WEEK"
    private BigDecimal baselineValue;
    private BigDecimal currentValue;

    // Detection Details
    private LocalDateTime detectedAt;
    private String detectionMethod; // "ISOLATION_FOREST", "AUTOENCODER", "STATISTICAL", "RULE_BASED"
    private String modelVersion;

    // Flags
    private Boolean isOutlier;
    private Boolean exceedsThreshold;
    private Boolean requiresReview;
    private Boolean isPotentialFraud;
    private Boolean isPotentialAML;

    // Recommendations
    private String recommendation; // "MONITOR", "REVIEW", "INVESTIGATE", "BLOCK"
    private Boolean requiresManualReview;

    // Metadata
    private LocalDateTime transactionDateTime;
    private String transactionType;
    private String notes;

    // Helper Methods
    public boolean isHighSeverity() {
        return "HIGH".equals(severity) || "CRITICAL".equals(severity) ||
                (severityScore != null && severityScore >= 70);
    }

    public boolean isStatisticallySignificant() {
        return zScore != null && zScore.abs().compareTo(BigDecimal.valueOf(3.0)) > 0;
    }

    public boolean requiresImmediateAction() {
        return "CRITICAL".equals(severity) ||
                requiresManualReview != null && requiresManualReview;
    }

    public boolean isHighlyAnomalous() {
        return anomalyScore != null && anomalyScore.compareTo(BigDecimal.valueOf(0.8)) > 0;
    }

    public boolean isLowProbabilityLegitimate() {
        return probabilityScore != null && probabilityScore.compareTo(BigDecimal.valueOf(0.3)) < 0;
    }

    public boolean shouldBlock() {
        return "BLOCK".equals(recommendation);
    }

    public BigDecimal getAbsoluteDeviation() {
        if (deviationAmount == null) return BigDecimal.ZERO;
        return deviationAmount.abs();
    }
}

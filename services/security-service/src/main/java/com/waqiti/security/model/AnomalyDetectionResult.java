package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Anomaly Detection Result
 *
 * Contains the complete result of anomaly detection analysis for authentication events.
 * This class aggregates multiple detected anomalies into an overall risk assessment.
 *
 * Architecture:
 * - detectedAnomalies: List of individual anomalies found (each with its own risk score)
 * - overallRiskScore: Aggregated risk score (sum of all anomaly scores, capped at 100)
 * - overallAnomalous: Boolean flag if ANY anomalies were detected
 * - overallConfidence: Average confidence across all detections
 *
 * @author Waqiti Security Team
 * @version 2.0.0 - Added aggregate fields for production use
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyDetectionResult {

    // Event identification
    private String eventId;
    private String userId;
    private Instant detectionTime;

    // Individual anomalies detected (core data)
    private List<DetectedAnomaly> detectedAnomalies;

    // Aggregate risk assessment (primary fields)
    @Builder.Default
    private Integer overallRiskScore = 0;  // Aggregated score from all anomalies (0-100)

    @Builder.Default
    private Boolean overallAnomalous = false;  // True if ANY anomalies detected

    @Builder.Default
    private Double overallConfidence = 0.0;  // Average confidence across all detections (0.0-1.0)

    // Legacy field for backward compatibility
    @Deprecated
    private Integer riskScore;  // Use overallRiskScore instead

    // Risk categorization
    private String riskLevel;  // LOW, MEDIUM, HIGH, CRITICAL

    // Action flags
    private Boolean requiresAdaptiveAuth;
    private Boolean enablesProfileUpdate;
    private Boolean requiresDeviceAnalysis;
    private Boolean enablesMLUpdate;
    private Boolean hasSecurityWorkflows;

    // Additional metadata
    private List<String> recommendations;
    private String detectionStrategy;
    private Long detectionTimeMs;

    /**
     * Get overall risk score (primary method)
     * This is the aggregate score across all detected anomalies
     */
    public Integer getOverallRiskScore() {
        return this.overallRiskScore != null ? this.overallRiskScore : 0;
    }

    /**
     * Check if this result contains any anomalies
     */
    public Boolean isOverallAnomalous() {
        return this.overallAnomalous != null ? this.overallAnomalous :
               (this.detectedAnomalies != null && !this.detectedAnomalies.isEmpty());
    }

    /**
     * Get overall confidence score
     */
    public Double getOverallConfidence() {
        return this.overallConfidence != null ? this.overallConfidence : 0.0;
    }

    /**
     * Get list of anomalies (alias for detectedAnomalies for backward compatibility)
     */
    public List<DetectedAnomaly> getAnomalies() {
        return this.detectedAnomalies != null ? this.detectedAnomalies : new ArrayList<>();
    }

    /**
     * Legacy method for backward compatibility
     * @deprecated Use getOverallRiskScore() instead
     */
    @Deprecated
    public Integer getRiskScore() {
        // Prefer overallRiskScore, fallback to legacy riskScore
        return this.overallRiskScore != null ? this.overallRiskScore : this.riskScore;
    }
}

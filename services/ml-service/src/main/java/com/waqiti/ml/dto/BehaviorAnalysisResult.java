package com.waqiti.ml.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

/**
 * Comprehensive behavioral analysis result with detailed risk assessment.
 * Production-ready DTO with validation, serialization, and complete metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BehaviorAnalysisResult {

    @NotBlank(message = "User ID is required")
    @Size(max = 36, message = "User ID must not exceed 36 characters")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "Transaction ID is required")
    @Size(max = 36, message = "Transaction ID must not exceed 36 characters")
    @JsonProperty("transaction_id")
    private String transactionId;

    @NotNull(message = "Timestamp is required")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @DecimalMin(value = "0.0", message = "Risk score must be non-negative")
    @DecimalMax(value = "100.0", message = "Risk score must not exceed 100")
    @JsonProperty("risk_score")
    private double riskScore;

    /**
     * Overall behavioral risk score (0.0 - 1.0 scale for ML consistency)
     * This is the primary score used by fraud detection engine
     */
    @DecimalMin(value = "0.0", message = "Overall behavior score must be non-negative")
    @DecimalMax(value = "1.0", message = "Overall behavior score must not exceed 1.0")
    @JsonProperty("overall_behavior_score")
    @Builder.Default
    private Double overallBehaviorScore = 0.0;

    @NotBlank(message = "Risk level is required")
    @Pattern(regexp = "MINIMAL|LOW|MEDIUM|HIGH|CRITICAL", message = "Invalid risk level")
    @JsonProperty("risk_level")
    private String riskLevel;

    /**
     * Get overall behavior score - converts from 0-100 riskScore if needed
     */
    public Double getOverallBehaviorScore() {
        if (overallBehaviorScore != null) {
            return overallBehaviorScore;
        }
        // Fallback: normalize riskScore (0-100) to 0.0-1.0 scale
        return riskScore / 100.0;
    }

    @DecimalMin(value = "0.0", message = "Confidence score must be non-negative")
    @DecimalMax(value = "1.0", message = "Confidence score must not exceed 1.0")
    @JsonProperty("confidence_score")
    private double confidenceScore;

    @NotBlank(message = "Recommended action is required")
    @Pattern(regexp = "APPROVE|ENHANCED_MONITORING|ADDITIONAL_VERIFICATION|MANUAL_REVIEW|BLOCK_TRANSACTION", 
             message = "Invalid recommended action")
    @JsonProperty("recommended_action")
    private String recommendedAction;

    @Valid
    @JsonProperty("risk_components")
    private Map<String, Double> riskComponents;

    @Size(max = 5000, message = "Analysis details must not exceed 5000 characters")
    @JsonProperty("analysis_details")
    private String analysisDetails;

    @NotBlank(message = "Analysis version is required")
    @JsonProperty("analysis_version")
    private String analysisVersion;

    @NotBlank(message = "Model version is required")
    @JsonProperty("model_version")
    private String modelVersion;

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;

    @Valid
    @JsonProperty("behavioral_indicators")
    private List<BehaviorIndicator> behavioralIndicators;

    @Valid
    @JsonProperty("anomaly_details")
    private AnomalyDetails anomalyDetails;

    @JsonProperty("threat_intelligence")
    private ThreatIntelligence threatIntelligence;

    @JsonProperty("compliance_flags")
    private ComplianceFlags complianceFlags;

    /**
     * Nested class for behavioral indicators
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehaviorIndicator {
        
        @NotBlank(message = "Indicator type is required")
        private String type;
        
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "100.0")
        private double score;
        
        @NotBlank(message = "Severity is required")
        @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL")
        private String severity;
        
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        private String description;
        
        private Map<String, Object> metadata;
    }

    /**
     * Nested class for anomaly detection details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyDetails {
        
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "1.0")
        @JsonProperty("anomaly_score")
        private double anomalyScore;
        
        @JsonProperty("anomaly_type")
        private String anomalyType;
        
        @JsonProperty("statistical_deviation")
        private double statisticalDeviation;
        
        @JsonProperty("isolation_score")
        private double isolationScore;
        
        @JsonProperty("cluster_analysis")
        private ClusterAnalysis clusterAnalysis;
        
        @Valid
        @JsonProperty("feature_importance")
        private Map<String, Double> featureImportance;
    }

    /**
     * Nested class for cluster analysis results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterAnalysis {
        
        @JsonProperty("cluster_id")
        private String clusterId;
        
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "1.0")
        @JsonProperty("distance_from_center")
        private double distanceFromCenter;
        
        @JsonProperty("cluster_size")
        private int clusterSize;
        
        @JsonProperty("cluster_type")
        private String clusterType; // NORMAL, SUSPICIOUS, FRAUDULENT
        
        @JsonProperty("similar_users")
        private List<String> similarUsers;
    }

    /**
     * Nested class for threat intelligence
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreatIntelligence {
        
        @JsonProperty("ip_reputation")
        private IpReputation ipReputation;
        
        @JsonProperty("device_reputation")
        private DeviceReputation deviceReputation;
        
        @JsonProperty("geolocation_risk")
        private GeolocationRisk geolocationRisk;
        
        @JsonProperty("known_attack_patterns")
        private List<AttackPattern> knownAttackPatterns;
    }

    /**
     * Nested class for IP reputation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IpReputation {
        
        @JsonProperty("reputation_score")
        private double reputationScore;
        
        @JsonProperty("threat_categories")
        private List<String> threatCategories;
        
        @JsonProperty("geographic_location")
        private String geographicLocation;
        
        @JsonProperty("is_tor_exit_node")
        private boolean isTorExitNode;
        
        @JsonProperty("is_vpn")
        private boolean isVpn;
        
        @JsonProperty("is_proxy")
        private boolean isProxy;
    }

    /**
     * Nested class for device reputation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceReputation {
        
        @JsonProperty("device_trust_score")
        private double deviceTrustScore;
        
        @JsonProperty("first_seen")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
        private LocalDateTime firstSeen;
        
        @JsonProperty("last_seen")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
        private LocalDateTime lastSeen;
        
        @JsonProperty("device_fingerprint_confidence")
        private double deviceFingerprintConfidence;
        
        @JsonProperty("jailbreak_detection")
        private boolean jailbreakDetected;
        
        @JsonProperty("emulator_detection")
        private boolean emulatorDetected;
    }

    /**
     * Nested class for geolocation risk
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeolocationRisk {
        
        @JsonProperty("location_risk_score")
        private double locationRiskScore;
        
        @JsonProperty("country_risk_level")
        private String countryRiskLevel;
        
        @JsonProperty("distance_from_usual_location")
        private double distanceFromUsualLocation;
        
        @JsonProperty("velocity_impossible")
        private boolean velocityImpossible;
        
        @JsonProperty("high_risk_region")
        private boolean highRiskRegion;
    }

    /**
     * Nested class for attack patterns
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttackPattern {
        
        @JsonProperty("pattern_id")
        private String patternId;
        
        @JsonProperty("pattern_name")
        private String patternName;
        
        @JsonProperty("match_confidence")
        private double matchConfidence;
        
        @JsonProperty("threat_level")
        private String threatLevel;
        
        @JsonProperty("mitre_attack_id")
        private String mitreAttackId;
    }

    /**
     * Nested class for compliance flags
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceFlags {
        
        @JsonProperty("aml_flag")
        private boolean amlFlag;
        
        @JsonProperty("kyc_verification_required")
        private boolean kycVerificationRequired;
        
        @JsonProperty("sar_filing_required")
        private boolean sarFilingRequired;
        
        @JsonProperty("enhanced_due_diligence")
        private boolean enhancedDueDiligence;
        
        @JsonProperty("pep_screening")
        private boolean pepScreening;
        
        @JsonProperty("sanctions_screening")
        private boolean sanctionsScreening;
        
        @JsonProperty("compliance_notes")
        private String complianceNotes;
    }

    /**
     * Helper method to determine if transaction requires manual review
     */
    public boolean requiresManualReview() {
        return "MANUAL_REVIEW".equals(recommendedAction) || 
               "BLOCK_TRANSACTION".equals(recommendedAction) ||
               riskScore >= 60.0;
    }

    /**
     * Helper method to check if transaction is high risk
     */
    public boolean isHighRisk() {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }

    /**
     * Helper method to get primary risk factor
     */
    public String getPrimaryRiskFactor() {
        if (riskComponents == null || riskComponents.isEmpty()) {
            return "UNKNOWN";
        }
        
        return riskComponents.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("UNKNOWN");
    }

    /**
     * Helper method to check if requires compliance action
     */
    public boolean requiresComplianceAction() {
        return complianceFlags != null && 
               (complianceFlags.amlFlag || 
                complianceFlags.sarFilingRequired || 
                complianceFlags.enhancedDueDiligence);
    }
}
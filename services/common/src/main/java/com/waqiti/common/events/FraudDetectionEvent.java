package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Fraud Detection Event - Published by ML Service
 * 
 * Contains comprehensive ML analysis results for fraud detection
 * including risk scores, confidence levels, and detected patterns
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudDetectionEvent {
    
    @NotBlank
    private String eventId;
    
    @NotBlank
    private String transactionId;
    
    @NotBlank
    private String userId;

    /**
     * Get userId as UUID with null-safe parsing
     * @return UUID representation of userId
     * @throws IllegalArgumentException if userId is not a valid UUID format
     */
    public java.util.UUID getUserIdAsUUID() {
        try {
            return java.util.UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid userId format: " + userId + ". Expected valid UUID string.", e);
        }
    }
    
    @NotNull
    private BigDecimal amount;
    
    @NotBlank
    private String currency;
    
    /**
     * ML-generated fraud score (0.0 to 1.0)
     * This is the primary fraud risk score from the ML model
     */
    @NotNull
    @Min(0)
    @Max(1)
    private Double fraudScore;

    /**
     * Backward compatibility alias for fraudScore
     *
     * DEPRECATED: Use getFraudScore() instead. This method exists for backward
     * compatibility with existing consumers that call getRiskScore().
     *
     * @return the fraud score (same as getFraudScore())
     * @deprecated Use {@link #getFraudScore()} instead. Will be removed in v3.0
     */
    @Deprecated(since = "2.1", forRemoval = true)
    public Double getRiskScore() {
        return fraudScore;
    }

    /**
     * Backward compatibility setter for fraudScore
     * @param riskScore the fraud score value
     * @deprecated Use {@link #setFraudScore(Double)} instead
     */
    @Deprecated(since = "2.1", forRemoval = true)
    public void setRiskScore(Double riskScore) {
        this.fraudScore = riskScore;
    }
    
    /**
     * Model confidence level (0.0 to 1.0)
     */
    @NotNull
    @Min(0)
    @Max(1)
    private Double confidence;
    
    /**
     * Is transaction flagged as fraudulent
     */
    @NotNull
    private Boolean isFraudulent;
    
    /**
     * Risk level based on ML analysis
     */
    @NotNull
    private FraudRiskLevel riskLevel;
    
    /**
     * Primary fraud type detected
     */
    private FraudType fraudType;
    
    /**
     * ML model version used for detection
     */
    @NotBlank
    private String modelVersion;
    
    /**
     * Model name (e.g., "RandomForest", "GradientBoosting", "NeuralNetwork")
     */
    @NotBlank
    private String modelName;
    
    /**
     * Detected fraud patterns/indicators
     */
    private List<String> fraudIndicators;
    
    /**
     * Anomaly scores for different aspects
     */
    private Map<String, Double> anomalyScores;
    
    /**
     * Feature importance scores
     */
    private Map<String, Double> featureImportance;
    
    /**
     * Geolocation analysis results
     */
    private GeolocationData geolocationData;
    
    /**
     * Device fingerprint analysis
     */
    private DeviceFingerprintData deviceData;
    
    /**
     * Behavioral analysis results
     */
    private BehaviorAnalysisData behaviorData;
    
    /**
     * Network analysis results
     */
    private NetworkAnalysisData networkData;
    
    /**
     * Transaction velocity analysis
     */
    private VelocityAnalysisData velocityData;
    
    /**
     * Recommended actions based on analysis
     */
    private List<RecommendedAction> recommendedActions;
    
    /**
     * Processing metadata
     */
    private ProcessingMetadata processingMetadata;
    
    /**
     * Event timestamp
     */
    @NotNull
    private LocalDateTime timestamp;
    
    /**
     * Source service that triggered the analysis
     */
    @NotBlank
    private String sourceService;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    // Enums and nested classes
    
    public enum FraudRiskLevel {
        CRITICAL, HIGH, MEDIUM, LOW, MINIMAL, INFO
    }
    
    public enum FraudType {
        ACCOUNT_TAKEOVER,
        IDENTITY_THEFT,
        CARD_NOT_PRESENT,
        MONEY_LAUNDERING,
        VELOCITY_FRAUD,
        SYNTHETIC_IDENTITY,
        RETURN_FRAUD,
        CHARGEBACK_FRAUD,
        MERCHANT_FRAUD,
        UNKNOWN
    }
    
    public enum RecommendedAction {
        BLOCK_TRANSACTION,
        REQUIRE_ADDITIONAL_VERIFICATION,
        ENABLE_ENHANCED_MONITORING,
        CREATE_MANUAL_REVIEW_CASE,
        UPDATE_RISK_PROFILE,
        NOTIFY_SECURITY_TEAM,
        FREEZE_ACCOUNT,
        REQUEST_DOCUMENT_VERIFICATION,
        ENABLE_STEP_UP_AUTH,
        LOG_FOR_INVESTIGATION
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeolocationData {
        private Double latitude;
        private Double longitude;
        private String country;
        private String region;
        private String city;
        private String ipAddress;
        private Boolean isVpn;
        private Boolean isTor;
        private Boolean isProxy;
        private Double riskScore;
        private String provider;
        private List<String> anomalies;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceFingerprintData {
        private String deviceId;
        private String fingerprint;
        private String userAgent;
        private String platform;
        private Boolean isNewDevice;
        private Double trustScore;
        private LocalDateTime lastSeen;
        private List<String> suspiciousAttributes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehaviorAnalysisData {
        private Double behaviorScore;
        private List<String> deviations;
        private Map<String, Double> patterns;
        private Boolean isTypicalBehavior;
        private LocalDateTime profileLastUpdated;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkAnalysisData {
        private String connectionType;
        private List<String> networkFlags;
        private Double networkRiskScore;
        private Boolean isHighRiskNetwork;
        private String isp;
        private String asn;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VelocityAnalysisData {
        private Integer transactionCount1h;
        private Integer transactionCount24h;
        private BigDecimal amountSum1h;
        private BigDecimal amountSum24h;
        private List<String> velocityViolations;
        private Double velocityScore;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingMetadata {
        private LocalDateTime processingStartTime;
        private LocalDateTime processingEndTime;
        private Long processingDurationMs;
        private String modelLoadTime;
        private Integer featuresUsed;
        private String processingNode;
    }
    
    /**
     * Factory method to create event from ML analysis result
     */
    public static FraudDetectionEvent fromResult(Object result) {
        // This would typically extract data from the ML service's FraudDetectionResult
        // For now, creating a basic structure that can be extended
        return FraudDetectionEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .fraudScore(0.0)
                .confidence(0.0)
                .isFraudulent(false)
                .riskLevel(FraudRiskLevel.INFO)
                .modelVersion("1.0")
                .modelName("DefaultModel")
                .sourceService("ml-service")
                .build();
    }
    
    /**
     * Determine if immediate action is required
     */
    public boolean requiresImmediateAction() {
        return riskLevel == FraudRiskLevel.CRITICAL || 
               (isFraudulent != null && isFraudulent) ||
               (fraudScore != null && fraudScore > 0.9);
    }
    
    /**
     * Get severity for alerting purposes
     */
    public String getSeverity() {
        if (riskLevel == null) return "INFO";
        
        return switch (riskLevel) {
            case CRITICAL -> "CRITICAL";
            case HIGH -> "HIGH";
            case MEDIUM -> "MEDIUM";
            case LOW -> "LOW";
            default -> "INFO";
        };
    }
    
    /**
     * Check if manual review is recommended
     */
    public boolean requiresManualReview() {
        return recommendedActions != null && 
               recommendedActions.contains(RecommendedAction.CREATE_MANUAL_REVIEW_CASE);
    }
    
    /**
     * Check if transaction should be blocked
     */
    public boolean shouldBlockTransaction() {
        return recommendedActions != null && 
               recommendedActions.contains(RecommendedAction.BLOCK_TRANSACTION);
    }
}
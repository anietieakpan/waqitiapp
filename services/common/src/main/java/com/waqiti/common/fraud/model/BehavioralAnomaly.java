package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Behavioral anomaly detection model for fraud monitoring
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehavioralAnomaly {

    @JsonProperty("anomaly_id")
    private String anomalyId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("anomaly_type")
    private AnomalyType anomalyType;

    @JsonProperty("type")
    private com.waqiti.common.fraud.model.AnomalyType type;

    @JsonProperty("severity")
    private String severity;

    @JsonProperty("confidence_score")
    private BigDecimal confidenceScore;

    @JsonProperty("deviation_score")
    private BigDecimal deviationScore;

    @JsonProperty("detected_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime detectedAt;

    @JsonProperty("expected_behavior")
    private String expectedBehavior;

    @JsonProperty("observed_behavior")
    private String observedBehavior;

    @JsonProperty("anomaly_description")
    private String anomalyDescription;

    @JsonProperty("description")
    private String description;

    @JsonProperty("confidence")
    private Double confidence;

    @JsonProperty("behavioral_indicators")
    private Map<String, Object> behavioralIndicators;

    @JsonProperty("context_data")
    private Map<String, Object> contextData;

    @JsonProperty("risk_factors")
    private Map<String, BigDecimal> riskFactors;

    @JsonProperty("recommended_action")
    private String recommendedAction;

    @JsonProperty("auto_action_taken")
    private Boolean autoActionTaken;

    @JsonProperty("requires_review")
    private Boolean requiresReview;

    /**
     * Types of behavioral anomalies
     */
    public enum AnomalyType {
        TYPING_PATTERN,
        NAVIGATION_PATTERN,
        MOUSE_MOVEMENT,
        SESSION_BEHAVIOR,
        TIME_PATTERN,
        INTERACTION_SPEED,
        FORM_COMPLETION,
        COPY_PASTE_BEHAVIOR,
        DEVICE_SWITCHING,
        LOCATION_JUMP,
        ACCESS_PATTERN,
        TRANSACTION_PATTERN,
        SPENDING_PATTERN,
        MERCHANT_PATTERN,
        COMMUNICATION_PATTERN,
        LOGIN_PATTERN,
        MULTIPLE_ACCOUNTS,
        BOT_BEHAVIOR,
        AUTOMATED_BEHAVIOR,
        UNUSUAL_SEQUENCE
    }

    /**
     * Check if anomaly is high risk
     */
    public boolean isHighRisk() {
        return "HIGH".equals(severity) || "CRITICAL".equals(severity) ||
               (confidenceScore != null && confidenceScore.compareTo(new BigDecimal("0.8")) >= 0);
    }

    /**
     * Check if immediate action required
     */
    public boolean requiresImmediateAction() {
        return isHighRisk() || Boolean.TRUE.equals(requiresReview);
    }
}
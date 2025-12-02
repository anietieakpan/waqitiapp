package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pattern match result for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternMatch {

    @JsonProperty("match_id")
    private String matchId;

    @JsonProperty("pattern_id")
    private String patternId;

    @JsonProperty("pattern_name")
    private String patternName;

    @JsonProperty("pattern_type")
    private String patternType;

    @JsonProperty("description")
    private String description;

    @JsonProperty("match_score")
    private BigDecimal matchScore;

    @JsonProperty("confidence_level")
    private BigDecimal confidenceLevel;

    @JsonProperty("matched_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime matchedAt;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("matched_elements")
    private List<String> matchedElements;

    @JsonProperty("pattern_attributes")
    private Map<String, Object> patternAttributes;

    @JsonProperty("match_details")
    private Map<String, Object> matchDetails;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("fraud_probability")
    private BigDecimal fraudProbability;

    @JsonProperty("historical_matches")
    private Integer historicalMatches;

    @JsonProperty("is_known_pattern")
    private Boolean isKnownPattern;

    @JsonProperty("requires_action")
    private Boolean requiresAction;

    @JsonProperty("action_taken")
    private String actionTaken;

    /**
     * Check if pattern match is significant
     */
    public boolean isSignificant() {
        return matchScore != null && matchScore.compareTo(new BigDecimal("0.7")) >= 0;
    }

    /**
     * Check if pattern indicates fraud
     */
    public boolean indicatesFraud() {
        return fraudProbability != null && fraudProbability.compareTo(new BigDecimal("0.6")) >= 0;
    }
}
package com.waqiti.common.fraud.rules;

import com.waqiti.common.fraud.model.FraudRiskLevel;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a violation of a fraud detection rule
 * Captures details about which rule was violated and the context
 */
@Data
@Builder
public class FraudRuleViolation {

    /**
     * Unique identifier for this violation
     */
    private String violationId;

    /**
     * The fraud rule that was violated
     */
    private String ruleId;

    /**
     * Name of the violated rule
     */
    private String ruleName;

    /**
     * Description of the violation
     */
    private String description;

    /**
     * Risk level associated with this violation
     */
    private FraudRiskLevel riskLevel;

    /**
     * Severity score (0.0 - 1.0)
     */
    private double severityScore;

    /**
     * Confidence level in the violation detection (0.0 - 1.0)
     */
    private double confidence;

    /**
     * The action recommended for this violation
     */
    private String recommendedAction;

    /**
     * Additional context and metadata
     */
    private Map<String, Object> context;

    /**
     * Transaction ID associated with the violation
     */
    private String transactionId;

    /**
     * User ID associated with the violation
     */
    private String userId;

    /**
     * When the violation was detected
     */
    @Builder.Default
    private LocalDateTime detectedAt = LocalDateTime.now();

    /**
     * Whether this violation should trigger an alert
     */
    @Builder.Default
    private boolean alertRequired = false;

    /**
     * Whether this violation should block the transaction
     */
    @Builder.Default
    private boolean blockingViolation = false;

    /**
     * Violation severity enum
     */
    private ViolationSeverity severity;

    /**
     * Risk score for this violation (0-100)
     */
    @Builder.Default
    private double riskScore = 0.0;

    /**
     * PRODUCTION FIX: Get severity (alias for backward compatibility)
     */
    public ViolationSeverity getSeverity() {
        if (severity != null) {
            return severity;
        }
        // Derive from severityScore
        if (severityScore >= 0.8) return ViolationSeverity.CRITICAL;
        if (severityScore >= 0.6) return ViolationSeverity.HIGH;
        if (severityScore >= 0.4) return ViolationSeverity.MEDIUM;
        return ViolationSeverity.LOW;
    }

    /**
     * PRODUCTION FIX: Get risk score
     */
    public double getRiskScore() {
        return riskScore > 0 ? riskScore : severityScore * 100.0;
    }

    /**
     * Get message (alias for description for backward compatibility)
     */
    public String getMessage() {
        return description;
    }

    /**
     * Violation severity enum with weights
     */
    public enum ViolationSeverity {
        LOW(1.0),
        MEDIUM(2.0),
        HIGH(3.0),
        CRITICAL(5.0);

        private final double weight;

        ViolationSeverity(double weight) {
            this.weight = weight;
        }

        public double getWeight() {
            return weight;
        }
    }
}

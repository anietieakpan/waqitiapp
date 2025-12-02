package com.waqiti.risk.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result from rule engine evaluation
 * Contains fired rules and their impact on risk assessment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEngineResult {

    private String evaluationId;
    private String ruleSetVersion;

    // Overall result
    private Double overallScore; // 0.0 to 1.0
    private String decision; // APPROVE, REVIEW, DECLINE
    private Boolean passed; // Did transaction pass all rules

    // Rules evaluated
    private Integer totalRulesEvaluated;
    private Integer rulesPassed;
    private Integer rulesFailed;
    private Integer rulesSkipped;

    // Fired rules
    private List<FiredRule> firedRules;
    private List<FiredRule> criticalRulesFired;
    private List<FiredRule> warningRulesFired;

    // Timing
    private Instant evaluatedAt;
    private Long evaluationTimeMs;

    // Decision factors
    private String primaryFailureReason;
    private List<String> allFailureReasons;

    private Map<String, Object> context; // Rule evaluation context

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FiredRule {
        private String ruleId;
        private String ruleName;
        private String ruleCategory; // VELOCITY, AMOUNT, GEOGRAPHY, DEVICE, PATTERN
        private String severity; // INFO, WARNING, CRITICAL
        private Double ruleScore; // Individual rule contribution
        private String action; // ALLOW, FLAG, REVIEW, BLOCK
        private String reason; // Why rule fired
        private Map<String, Object> ruleData; // Data used in evaluation
        private Instant firedAt;
    }
}

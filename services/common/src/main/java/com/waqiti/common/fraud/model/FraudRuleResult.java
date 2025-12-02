package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fraud rule result
 */
@Data
@Builder
@Jacksonized
public class FraudRuleResult {
    private String userId;
    private String transactionId;
    private String ruleSetId;
    private String ruleId;
    private boolean ruleFired;
    private double ruleScore;
    private String action;
    private List<String> reasons;
    private String severity;
    private Instant evaluationTimestamp;

    // Extended fields for ComprehensiveFraudBlacklistService
    private List<FraudRuleEvaluation> evaluations;
    private List<FraudRuleViolation> violations;
    private RuleViolationScore overallScore;
    private FraudEnforcementAction enforcementAction;
    private int rulesEvaluated;
    private int rulesViolated;
    private boolean shouldBlock;
    private boolean shouldFlag;
    private LocalDateTime evaluatedAt;
    private String errorMessage;

    /**
     * Get rule evaluations
     */
    public List<FraudRuleEvaluation> evaluations() {
        return evaluations != null ? evaluations : new java.util.ArrayList<>();
    }

    /**
     * Check if transaction should be blocked
     */
    public boolean shouldBlock() {
        return shouldBlock;
    }
}
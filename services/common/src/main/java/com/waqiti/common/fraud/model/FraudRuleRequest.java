package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Fraud rule request
 *
 * PRODUCTION FIX: Added missing fields for ComprehensiveFraudBlacklistService
 */
@Data
@Builder
@Jacksonized
public class FraudRuleRequest {
    private String ruleId;
    private String ruleName;
    private Map<String, Object> ruleParameters;
    private String transactionId;
    private Map<String, Object> transactionData;
    private String userId;
    private String ruleSetId;
    private Map<String, Object> evaluationContext;
    private String enforcementPolicy;

    // PRODUCTION FIX: Additional fields needed by ComprehensiveFraudBlacklistService
    private BigDecimal amount;
    private Integer recentTransactionCount;
    private String blacklistMatch;
    
    /**
     * Get rule set identifier
     */
    public String getRuleSetId() {
        return ruleSetId != null ? ruleSetId : "default";
    }
    
    /**
     * Get evaluation context
     */
    public Map<String, Object> getEvaluationContext() {
        return evaluationContext != null ? evaluationContext : transactionData;
    }
    
    /**
     * Get enforcement policy
     */
    public String getEnforcementPolicy() {
        return enforcementPolicy != null ? enforcementPolicy : "strict";
    }
}
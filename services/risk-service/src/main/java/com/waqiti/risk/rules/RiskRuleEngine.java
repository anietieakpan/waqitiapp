package com.waqiti.risk.rules;

import com.waqiti.risk.domain.RiskFactor;
import com.waqiti.risk.dto.RiskFactorScore;
import com.waqiti.risk.dto.RuleEngineResult;
import com.waqiti.risk.dto.TransactionRiskRequest;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for Risk Rule Engine
 *
 * Provides rule-based risk assessment capabilities for:
 * - Transaction validation
 * - Pattern matching
 * - Threshold checks
 * - Business rule evaluation
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
public interface RiskRuleEngine {

    /**
     * Evaluate risk rules synchronously
     *
     * @param request Transaction risk request
     * @param factorScores Calculated risk factor scores
     * @return Rule engine result with triggered rules and score
     */
    RuleEngineResult evaluate(TransactionRiskRequest request,
                             Map<RiskFactor, RiskFactorScore> factorScores);

    /**
     * Evaluate risk rules asynchronously
     *
     * @param request Transaction risk request
     * @param factorScores Calculated risk factor scores
     * @return CompletableFuture containing rule engine result
     */
    CompletableFuture<RuleEngineResult> evaluateAsync(TransactionRiskRequest request,
                                                      Map<RiskFactor, RiskFactorScore> factorScores);

    /**
     * Load rules from DRL files or database
     */
    void loadRules();

    /**
     * Reload rules (for dynamic rule updates)
     */
    void reloadRules();

    /**
     * Validate rule syntax
     *
     * @param ruleDefinition Rule definition (DRL format)
     * @return true if valid, false otherwise
     */
    boolean validateRule(String ruleDefinition);

    /**
     * Get rule engine statistics
     *
     * @return Map of statistics (rules loaded, evaluations count, etc.)
     */
    Map<String, Object> getStatistics();
}

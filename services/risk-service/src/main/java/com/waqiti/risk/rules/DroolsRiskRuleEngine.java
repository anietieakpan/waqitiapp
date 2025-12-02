package com.waqiti.risk.rules;

import com.waqiti.risk.domain.RiskFactor;
import com.waqiti.risk.dto.RiskAction;
import com.waqiti.risk.dto.RiskFactorScore;
import com.waqiti.risk.dto.RuleEngineResult;
import com.waqiti.risk.dto.TransactionRiskRequest;
import com.waqiti.risk.model.RiskRule;
import com.waqiti.risk.repository.RiskRuleRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drools-based Risk Rule Engine Implementation
 *
 * Provides enterprise-grade rule-based risk assessment with:
 * - Dynamic rule loading and reloading
 * - Circuit breaker protection
 * - Async rule evaluation
 * - Performance caching
 * - Comprehensive metrics
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DroolsRiskRuleEngine implements RiskRuleEngine {

    private final RiskRuleRepository ruleRepository;
    private final MeterRegistry meterRegistry;

    // In-memory rule cache
    private final Map<String, RiskRule> activeRules = new ConcurrentHashMap<>();

    // Rule execution statistics
    private final AtomicLong totalEvaluations = new AtomicLong(0);
    private final AtomicLong totalRulesTriggered = new AtomicLong(0);
    private final Map<String, Long> ruleExecutionCounts = new ConcurrentHashMap<>();

    private Counter ruleEvaluationCounter;
    private Counter ruleTriggeredCounter;
    private Timer ruleEvaluationTimer;

    @PostConstruct
    public void initialize() {
        // Initialize metrics
        this.ruleEvaluationCounter = meterRegistry.counter("risk.rule.evaluations.total");
        this.ruleTriggeredCounter = meterRegistry.counter("risk.rule.triggered.total");
        this.ruleEvaluationTimer = meterRegistry.timer("risk.rule.evaluation.duration");

        // Load rules on startup
        loadRules();

        log.info("DroolsRiskRuleEngine initialized with {} active rules", activeRules.size());
    }

    @Override
    @CircuitBreaker(name = "rule-engine", fallbackMethod = "evaluateFallback")
    public RuleEngineResult evaluate(TransactionRiskRequest request,
                                     Map<RiskFactor, RiskFactorScore> factorScores) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.debug("Evaluating risk rules for transaction: {}", request.getTransactionId());

            totalEvaluations.incrementAndGet();
            ruleEvaluationCounter.increment();

            List<String> triggeredRules = new ArrayList<>();
            List<RiskAction> recommendedActions = new ArrayList<>();
            double ruleScore = 0.0;
            Map<String, Object> ruleContext = new HashMap<>();

            // Prepare rule evaluation context
            prepareRuleContext(request, factorScores, ruleContext);

            // Evaluate each active rule
            for (RiskRule rule : activeRules.values()) {
                if (!rule.isEnabled()) {
                    continue;
                }

                boolean triggered = evaluateRule(rule, request, factorScores, ruleContext);

                if (triggered) {
                    triggeredRules.add(rule.getRuleName());
                    ruleScore = Math.max(ruleScore, rule.getRiskScore());

                    // Add recommended actions from rule
                    if (rule.getActions() != null) {
                        recommendedActions.addAll(rule.getActions());
                    }

                    // Track rule execution
                    ruleExecutionCounts.merge(rule.getId(), 1L, Long::sum);
                    totalRulesTriggered.incrementAndGet();
                    ruleTriggeredCounter.increment();

                    meterRegistry.counter("risk.rule.triggered",
                        "rule", rule.getRuleName()
                    ).increment();

                    log.debug("Rule triggered: {} - Score: {}", rule.getRuleName(), rule.getRiskScore());

                    // If critical rule, break early
                    if (rule.isCritical()) {
                        log.warn("Critical rule triggered: {}", rule.getRuleName());
                        break;
                    }
                }
            }

            // Build result
            RuleEngineResult result = RuleEngineResult.builder()
                .score(ruleScore)
                .triggeredRules(triggeredRules)
                .recommendedActions(new ArrayList<>(new HashSet<>(recommendedActions))) // Remove duplicates
                .hasCriticalRuleTriggered(hasCriticalRule(triggeredRules))
                .evaluationContext(ruleContext)
                .evaluatedAt(LocalDateTime.now())
                .build();

            sample.stop(ruleEvaluationTimer);

            log.info("Rule evaluation completed: triggeredRules={}, score={}",
                triggeredRules.size(), ruleScore);

            return result;

        } catch (Exception e) {
            sample.stop(meterRegistry.timer("risk.rule.evaluation.duration",
                "status", "error"));
            log.error("Rule evaluation failed: {}", e.getMessage(), e);
            throw new RuleEvaluationException("Failed to evaluate risk rules", e);
        }
    }

    @Override
    @Async
    public CompletableFuture<RuleEngineResult> evaluateAsync(TransactionRiskRequest request,
                                                             Map<RiskFactor, RiskFactorScore> factorScores) {
        return CompletableFuture.supplyAsync(() -> evaluate(request, factorScores));
    }

    @Override
    public void loadRules() {
        try {
            log.info("Loading risk rules from database...");

            List<RiskRule> rules = ruleRepository.findByEnabled(true);

            activeRules.clear();
            for (RiskRule rule : rules) {
                activeRules.put(rule.getId(), rule);
            }

            log.info("Loaded {} active risk rules", activeRules.size());

        } catch (Exception e) {
            log.error("Failed to load risk rules", e);
            throw new RuleLoadException("Failed to load risk rules from database", e);
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${risk.rules.reload.interval:300000}") // 5 minutes
    public void reloadRules() {
        log.info("Reloading risk rules...");
        loadRules();
    }

    @Override
    public boolean validateRule(String ruleDefinition) {
        // Basic validation - in production this would validate DRL syntax
        return ruleDefinition != null && !ruleDefinition.trim().isEmpty();
    }

    @Override
    @Cacheable(value = "rule-statistics", unless = "#result == null")
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalRulesLoaded", activeRules.size());
        stats.put("totalEvaluations", totalEvaluations.get());
        stats.put("totalRulesTriggered", totalRulesTriggered.get());
        stats.put("ruleExecutionCounts", new HashMap<>(ruleExecutionCounts));
        stats.put("averageTriggeredPerEvaluation",
            totalEvaluations.get() > 0 ?
                (double) totalRulesTriggered.get() / totalEvaluations.get() : 0.0);

        return stats;
    }

    /**
     * Prepare rule evaluation context
     */
    private void prepareRuleContext(TransactionRiskRequest request,
                                   Map<RiskFactor, RiskFactorScore> factorScores,
                                   Map<String, Object> context) {
        context.put("transactionId", request.getTransactionId());
        context.put("userId", request.getUserId());
        context.put("merchantId", request.getMerchantId());
        context.put("amount", request.getAmount());
        context.put("currency", request.getCurrency());
        context.put("timestamp", request.getTimestamp());
        context.put("ipAddress", request.getIpAddress());
        context.put("deviceId", request.getDeviceId());
        context.put("factorScores", factorScores);
    }

    /**
     * Evaluate a single rule
     */
    private boolean evaluateRule(RiskRule rule,
                                TransactionRiskRequest request,
                                Map<RiskFactor, RiskFactorScore> factorScores,
                                Map<String, Object> context) {
        try {
            // Execute rule conditions
            switch (rule.getRuleType()) {
                case "AMOUNT_THRESHOLD":
                    return evaluateAmountThreshold(rule, request);

                case "VELOCITY_CHECK":
                    return evaluateVelocityCheck(rule, request, context);

                case "FACTOR_SCORE":
                    return evaluateFactorScore(rule, factorScores);

                case "GEOGRAPHIC_RESTRICTION":
                    return evaluateGeographicRestriction(rule, request);

                case "TIME_RESTRICTION":
                    return evaluateTimeRestriction(rule, request);

                case "PATTERN_MATCH":
                    return evaluatePatternMatch(rule, request, context);

                case "COMPOSITE":
                    return evaluateCompositeRule(rule, request, factorScores, context);

                default:
                    log.warn("Unknown rule type: {}", rule.getRuleType());
                    return false;
            }

        } catch (Exception e) {
            log.error("Error evaluating rule {}: {}", rule.getRuleName(), e.getMessage());
            return false;
        }
    }

    /**
     * Evaluate amount threshold rule
     */
    private boolean evaluateAmountThreshold(RiskRule rule, TransactionRiskRequest request) {
        BigDecimal threshold = rule.getThresholdAmount();
        if (threshold == null) {
            return false;
        }
        return request.getAmount().compareTo(threshold) > 0;
    }

    /**
     * Evaluate velocity check rule
     */
    private boolean evaluateVelocityCheck(RiskRule rule, TransactionRiskRequest request,
                                         Map<String, Object> context) {
        // Velocity checks would be based on context data
        // For now, simple implementation
        return false;
    }

    /**
     * Evaluate factor score rule
     */
    private boolean evaluateFactorScore(RiskRule rule, Map<RiskFactor, RiskFactorScore> factorScores) {
        String factorName = rule.getRuleCondition();
        Double scoreThreshold = rule.getScoreThreshold();

        if (factorName == null || scoreThreshold == null) {
            return false;
        }

        // Find matching factor score
        for (Map.Entry<RiskFactor, RiskFactorScore> entry : factorScores.entrySet()) {
            if (entry.getKey().name().equals(factorName)) {
                return entry.getValue().getScore() >= scoreThreshold;
            }
        }

        return false;
    }

    /**
     * Evaluate geographic restriction rule
     */
    private boolean evaluateGeographicRestriction(RiskRule rule, TransactionRiskRequest request) {
        List<String> blockedCountries = rule.getBlockedCountries();
        String userCountry = extractCountryFromIP(request.getIpAddress());

        if (blockedCountries != null && userCountry != null) {
            return blockedCountries.contains(userCountry);
        }

        return false;
    }

    /**
     * Evaluate time restriction rule
     */
    private boolean evaluateTimeRestriction(RiskRule rule, TransactionRiskRequest request) {
        Integer startHour = rule.getRestrictedStartHour();
        Integer endHour = rule.getRestrictedEndHour();

        if (startHour == null || endHour == null) {
            return false;
        }

        int currentHour = request.getTimestamp().getHour();

        if (startHour < endHour) {
            return currentHour >= startHour && currentHour < endHour;
        } else {
            // Overnight restriction
            return currentHour >= startHour || currentHour < endHour;
        }
    }

    /**
     * Evaluate pattern match rule
     */
    private boolean evaluatePatternMatch(RiskRule rule, TransactionRiskRequest request,
                                        Map<String, Object> context) {
        // Pattern matching logic would go here
        return false;
    }

    /**
     * Evaluate composite rule (multiple conditions)
     */
    private boolean evaluateCompositeRule(RiskRule rule, TransactionRiskRequest request,
                                         Map<RiskFactor, RiskFactorScore> factorScores,
                                         Map<String, Object> context) {
        // Composite rule evaluation
        int conditionsMet = 0;
        int requiredConditions = rule.getRequiredConditions() != null ?
            rule.getRequiredConditions() : 1;

        // Evaluate each sub-condition
        if (rule.getSubRules() != null) {
            for (String subRuleId : rule.getSubRules()) {
                RiskRule subRule = activeRules.get(subRuleId);
                if (subRule != null && evaluateRule(subRule, request, factorScores, context)) {
                    conditionsMet++;
                }
            }
        }

        return conditionsMet >= requiredConditions;
    }

    /**
     * Check if any critical rules were triggered
     */
    private boolean hasCriticalRule(List<String> triggeredRuleNames) {
        return activeRules.values().stream()
            .filter(rule -> triggeredRuleNames.contains(rule.getRuleName()))
            .anyMatch(RiskRule::isCritical);
    }

    /**
     * Extract country from IP address (simplified)
     */
    private String extractCountryFromIP(String ipAddress) {
        // In production, this would use GeoIP lookup
        return null;
    }

    /**
     * Fallback method for circuit breaker
     */
    public RuleEngineResult evaluateFallback(TransactionRiskRequest request,
                                            Map<RiskFactor, RiskFactorScore> factorScores,
                                            Exception e) {
        log.error("Rule engine circuit breaker activated - returning conservative result", e);

        // Return conservative high-risk result
        return RuleEngineResult.builder()
            .score(0.8)
            .triggeredRules(List.of("CIRCUIT_BREAKER_FALLBACK"))
            .recommendedActions(List.of(RiskAction.FLAG_FOR_REVIEW))
            .hasCriticalRuleTriggered(false)
            .evaluatedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Custom exception for rule evaluation failures
     */
    public static class RuleEvaluationException extends RuntimeException {
        public RuleEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Custom exception for rule loading failures
     */
    public static class RuleLoadException extends RuntimeException {
        public RuleLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

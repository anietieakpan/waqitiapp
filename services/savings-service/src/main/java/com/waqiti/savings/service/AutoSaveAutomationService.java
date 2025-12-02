package com.waqiti.savings.service;

import com.waqiti.savings.domain.*;
import com.waqiti.savings.dto.request.*;
import com.waqiti.savings.dto.response.*;
import com.waqiti.savings.exception.*;
import com.waqiti.savings.repository.*;
import com.waqiti.savings.ml.SpendingPatternAnalyzer;
import com.waqiti.savings.ml.SavingsOptimizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Intelligent Auto-Save Automation Service
 * 
 * Provides sophisticated automated savings capabilities with ML-driven optimization,
 * behavioral triggers, smart rules, and predictive savings recommendations
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AutoSaveAutomationService {

    @Lazy
    private final AutoSaveAutomationService self;
    private final AutoSaveRuleRepository ruleRepository;
    private final SavingsGoalRepository goalRepository;
    private final SavingsContributionRepository contributionRepository;
    private final TransactionAnalysisService transactionService;
    private final BalanceMonitoringService balanceService;
    private final IncomeDetectionService incomeService;
    private final SpendingPatternAnalyzer patternAnalyzer;
    private final SavingsOptimizer savingsOptimizer;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Mappers (added in Phase 2)
    private final com.waqiti.savings.mapper.AutoSaveRuleMapper autoSaveRuleMapper;
    private final com.waqiti.savings.mapper.ContributionMapper contributionMapper;

    // Configuration constants
    private static final BigDecimal DEFAULT_ROUND_UP = new BigDecimal("1.00");
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int BATCH_SIZE = 100;

    /**
     * Create intelligent auto-save rule with ML optimization
     */
    @Transactional
    public AutoSaveRuleResponse createAutoSaveRule(UUID userId, CreateAutoSaveRuleRequest request) {
        log.info("Creating auto-save rule for user: {} type: {}", userId, request.getRuleType());

        try {
            // Validate request and goal
            validateRuleRequest(request);
            SavingsGoal goal = validateAndGetGoal(request.getGoalId(), userId);

            // Analyze user's financial behavior
            FinancialBehavior behavior = self.analyzeFinancialBehavior(userId);

            // Build auto-save rule with optimizations
            AutoSaveRule rule = buildAutoSaveRule(userId, goal.getId(), request, behavior);

            // Apply ML-based optimizations
            optimizeRuleParameters(rule, behavior);

            // Calculate initial execution schedule
            calculateExecutionSchedule(rule);

            // Validate rule feasibility
            RuleFeasibility feasibility = validateRuleFeasibility(rule, behavior);
            if (!feasibility.isFeasible()) {
                throw new RuleNotFeasibleException(
                    "Rule not feasible: " + feasibility.getReason());
            }

            // Save rule
            rule = ruleRepository.save(rule);

            // Schedule first execution if time-based
            if (rule.getTriggerType() == AutoSaveRule.TriggerType.TIME_BASED) {
                scheduleRuleExecution(rule);
            }

            // Send confirmation
            notificationService.sendAutoSaveRuleCreatedNotification(userId, rule, goal);

            log.info("Auto-save rule created: {} for goal: {}", rule.getId(), goal.getId());
            return mapToRuleResponse(rule, feasibility);

        } catch (Exception e) {
            log.error("Failed to create auto-save rule for user: {}", userId, e);
            throw new AutoSaveException("Failed to create auto-save rule: " + e.getMessage(), e);
        }
    }

    /**
     * Process income-based auto-saves
     */
    @Async
    public CompletableFuture<IncomeAutoSaveResult> processIncomeAutoSave(
            UUID userId, BigDecimal incomeAmount, String incomeSource) {
        
        log.info("Processing income auto-save for user: {} amount: {} source: {}", 
                userId, incomeAmount, incomeSource);

        try {
            // Find active income-based rules
            List<AutoSaveRule> incomeRules = ruleRepository
                    .findActiveRulesByTypeAndUserId(
                        AutoSaveRule.RuleType.PERCENTAGE_OF_INCOME, userId);

            if (incomeRules.isEmpty()) {
                return CompletableFuture.completedFuture(
                    IncomeAutoSaveResult.noRules());
            }

            BigDecimal totalSaved = BigDecimal.ZERO;
            List<IncomeAutoSaveExecution> executions = new ArrayList<>();

            // Sort rules by priority
            incomeRules.sort(Comparator.comparing(AutoSaveRule::getPriority).reversed());

            for (AutoSaveRule rule : incomeRules) {
                try {
                    // Calculate save amount based on percentage
                    BigDecimal saveAmount = incomeAmount
                            .multiply(rule.getPercentage())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                    // Apply rule limits
                    saveAmount = applyRuleLimits(rule, saveAmount);

                    if (saveAmount.compareTo(BigDecimal.ZERO) > 0) {
                        // Execute the save
                        AutoSaveExecution execution = executeAutoSave(
                                rule, saveAmount, "Income from " + incomeSource);

                        if (execution.isSuccessful()) {
                            totalSaved = totalSaved.add(saveAmount);
                            executions.add(new IncomeAutoSaveExecution(
                                    rule.getId(), saveAmount, true));
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to process income auto-save for rule: {}", rule.getId(), e);
                    executions.add(new IncomeAutoSaveExecution(
                            rule.getId(), BigDecimal.ZERO, false));
                }
            }

            // Send summary notification
            if (totalSaved.compareTo(BigDecimal.ZERO) > 0) {
                notificationService.sendIncomeAutoSaveNotification(
                        userId, incomeAmount, totalSaved, executions.size());
            }

            return CompletableFuture.completedFuture(
                    IncomeAutoSaveResult.success(totalSaved, executions));

        } catch (Exception e) {
            log.error("Failed to process income auto-save for user: {}", userId, e);
            return CompletableFuture.completedFuture(
                    IncomeAutoSaveResult.error(e.getMessage()));
        }
    }

    /**
     * Process behavioral trigger-based auto-saves
     */
    @Async
    public void processBehavioralTriggers(UUID userId, BehavioralEvent event) {
        log.debug("Processing behavioral trigger for user: {} event: {}", userId, event.getType());

        try {
            // Find rules with matching triggers
            List<AutoSaveRule> triggeredRules = ruleRepository
                    .findRulesByTriggerCondition(userId, event.getType().toString());

            for (AutoSaveRule rule : triggeredRules) {
                try {
                    // Evaluate trigger conditions
                    if (evaluateTriggerConditions(rule, event)) {
                        // Calculate dynamic save amount based on event
                        BigDecimal saveAmount = calculateBehavioralSaveAmount(rule, event);

                        if (saveAmount.compareTo(BigDecimal.ZERO) > 0) {
                            // Execute the save
                            executeAutoSave(rule, saveAmount, 
                                "Triggered by " + event.getType().getDescription());

                            log.info("Behavioral auto-save executed for rule: {} amount: {}", 
                                    rule.getId(), saveAmount);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to process behavioral trigger for rule: {}", rule.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process behavioral triggers for user: {}", userId, e);
        }
    }

    /**
     * Smart spare change calculation with dynamic rounding
     */
    public BigDecimal calculateSmartSpareChange(
            UUID userId, BigDecimal transactionAmount, String merchantCategory) {
        
        try {
            // Get user's spare change preferences
            SpareChangePreferences preferences = getUserSpareChangePreferences(userId);

            // Base round-up calculation
            BigDecimal roundUpTo = preferences.getRoundUpAmount() != null ? 
                    preferences.getRoundUpAmount() : DEFAULT_ROUND_UP;

            BigDecimal remainder = transactionAmount.remainder(roundUpTo);
            BigDecimal spareChange = remainder.compareTo(BigDecimal.ZERO) == 0 ? 
                    BigDecimal.ZERO : roundUpTo.subtract(remainder);

            // Apply dynamic multipliers based on category
            spareChange = applyCategoryMultiplier(spareChange, merchantCategory, preferences);

            // Apply boost for certain conditions
            if (preferences.isBoostEnabled()) {
                spareChange = applySpareChangeBoost(spareChange, userId);
            }

            return spareChange;

        } catch (Exception e) {
            log.warn("Failed to calculate smart spare change for user: {}", userId, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Optimize existing auto-save rules using ML
     */
    @Scheduled(cron = "0 0 2 * * SUN") // Weekly optimization
    @Async
    public void optimizeAutoSaveRules() {
        log.info("Starting weekly auto-save rule optimization");

        try {
            // Get all active rules
            List<AutoSaveRule> activeRules = ruleRepository.findAllActiveRules();

            for (AutoSaveRule rule : activeRules) {
                try {
                    optimizeIndividualRule(rule);
                } catch (Exception e) {
                    log.warn("Failed to optimize rule: {}", rule.getId(), e);
                }
            }

            log.info("Completed auto-save rule optimization for {} rules", activeRules.size());

        } catch (Exception e) {
            log.error("Error in auto-save optimization job", e);
        }
    }

    /**
     * Optimize individual rule based on performance
     */
    private void optimizeIndividualRule(AutoSaveRule rule) {
        // Get rule performance metrics
        RulePerformanceMetrics metrics = calculateRulePerformance(rule);

        // Get user's current financial situation
        FinancialBehavior behavior = self.analyzeFinancialBehavior(rule.getUserId());

        // Use ML to suggest optimizations
        RuleOptimizationSuggestions suggestions = savingsOptimizer
                .suggestRuleOptimizations(rule, metrics, behavior);

        // Apply optimizations if confidence is high
        if (suggestions.getConfidence() > 0.8) {
            applyOptimizationSuggestions(rule, suggestions);

            // Notify user of optimizations
            notificationService.sendRuleOptimizedNotification(
                    rule.getUserId(), rule, suggestions);

            log.info("Optimized rule: {} with {} changes", 
                    rule.getId(), suggestions.getChanges().size());
        }
    }

    /**
     * Process balance-triggered auto-saves
     */
    @Scheduled(fixedDelay = 3600000) // Check every hour
    @Async
    public void processBalanceTriggeredSaves() {
        log.debug("Processing balance-triggered auto-saves");

        try {
            // Get rules triggered by balance thresholds
            List<AutoSaveRule> balanceRules = ruleRepository
                    .findRulesByTriggerType(AutoSaveRule.TriggerType.BALANCE_BASED);

            for (AutoSaveRule rule : balanceRules) {
                try {
                    processBalanceRule(rule);
                } catch (Exception e) {
                    log.warn("Failed to process balance rule: {}", rule.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error processing balance-triggered saves", e);
        }
    }

    /**
     * Process a balance-triggered rule
     */
    private void processBalanceRule(AutoSaveRule rule) {
        // Get current balance
        BigDecimal currentBalance = balanceService.getCurrentBalance(rule.getUserId());

        // Get threshold from rule conditions
        BigDecimal threshold = extractBalanceThreshold(rule.getTriggerConditions());

        if (currentBalance.compareTo(threshold) > 0) {
            // Calculate excess amount to save
            BigDecimal excessAmount = currentBalance.subtract(threshold);
            
            // Apply rule percentage if defined
            if (rule.getPercentage() != null) {
                excessAmount = excessAmount
                        .multiply(rule.getPercentage())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }

            // Apply limits
            excessAmount = applyRuleLimits(rule, excessAmount);

            if (excessAmount.compareTo(BigDecimal.ZERO) > 0) {
                executeAutoSave(rule, excessAmount, "Balance threshold exceeded");
            }
        }
    }

    /**
     * Create milestone-based auto-save rules
     */
    public List<AutoSaveRule> createMilestoneRules(
            UUID userId, UUID goalId, MilestoneRulesRequest request) {
        
        log.info("Creating milestone-based rules for goal: {}", goalId);

        try {
            SavingsGoal goal = goalRepository.findById(goalId)
                    .orElseThrow(() -> new GoalNotFoundException("Goal not found"));

            List<AutoSaveRule> milestoneRules = new ArrayList<>();

            // Create rules for each milestone percentage
            for (Integer milestonePercent : request.getMilestonePercentages()) {
                BigDecimal milestoneAmount = goal.getTargetAmount()
                        .multiply(BigDecimal.valueOf(milestonePercent))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                AutoSaveRule rule = AutoSaveRule.builder()
                        .userId(userId)
                        .goalId(goalId)
                        .ruleType(AutoSaveRule.RuleType.GOAL_BASED)
                        .triggerType(AutoSaveRule.TriggerType.MILESTONE_BASED)
                        .amount(request.getBonusAmount())
                        .isActive(true)
                        .priority(8)
                        .triggerConditions(Map.of(
                                "milestone_percent", milestonePercent,
                                "milestone_amount", milestoneAmount.toString(),
                                "triggered", false
                        ))
                        .build();

                milestoneRules.add(rule);
            }

            // Save all milestone rules
            milestoneRules = ruleRepository.saveAll(milestoneRules);

            log.info("Created {} milestone rules for goal: {}", milestoneRules.size(), goalId);
            return milestoneRules;

        } catch (Exception e) {
            log.error("Failed to create milestone rules for goal: {}", goalId, e);
            throw new AutoSaveException("Failed to create milestone rules", e);
        }
    }

    /**
     * Execute auto-save with retry logic
     */
    private AutoSaveExecution executeAutoSave(
            AutoSaveRule rule, BigDecimal amount, String description) {
        
        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                // Get goal
                SavingsGoal goal = goalRepository.findById(rule.getGoalId())
                        .orElseThrow(() -> new GoalNotFoundException("Goal not found"));

                // Process payment
                PaymentResult payment = paymentService.processAutoSavePayment(
                        rule.getUserId(),
                        goal.getAccountId(),
                        rule.getFundingSourceId(),
                        amount,
                        description
                );

                if (payment.isSuccessful()) {
                    // Create contribution
                    SavingsContribution contribution = createAutoSaveContribution(
                            goal, rule, amount, description);

                    // Update goal
                    updateGoalWithContribution(goal, amount);

                    // Update rule statistics
                    rule.recordSuccess(amount);
                    ruleRepository.save(rule);

                    // Publish event
                    publishAutoSaveEvent(rule, goal, amount);

                    return AutoSaveExecution.success(amount, contribution.getId());
                } else {
                    throw new PaymentFailedException(payment.getError());
                }

            } catch (Exception e) {
                lastException = e;
                attempts++;
                
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(1000 * attempts); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Record failure after all retries
        rule.recordFailure(lastException != null ? lastException.getMessage() : "Unknown error");
        ruleRepository.save(rule);

        return AutoSaveExecution.failure(lastException != null ? lastException.getMessage() : "Failed after retries");
    }

    /**
     * Apply ML-based rule parameter optimization
     */
    private void optimizeRuleParameters(AutoSaveRule rule, FinancialBehavior behavior) {
        try {
            // Use ML to optimize amount
            if (rule.getRuleType() == AutoSaveRule.RuleType.FIXED_AMOUNT) {
                BigDecimal optimalAmount = savingsOptimizer.calculateOptimalSaveAmount(
                        behavior.getDisposableIncome(),
                        behavior.getSavingsRate(),
                        behavior.getExpenseVolatility()
                );
                
                if (rule.getAmount() == null || 
                    optimalAmount.compareTo(rule.getAmount()) < 0) {
                    rule.setAmount(optimalAmount);
                }
            }

            // Optimize execution frequency
            if (rule.getFrequency() == AutoSaveRule.Frequency.CUSTOM) {
                OptimalFrequency optimal = savingsOptimizer.determineOptimalFrequency(
                        behavior.getIncomeFrequency(),
                        behavior.getSpendingPattern()
                );
                applyOptimalFrequency(rule, optimal);
            }

            // Set intelligent limits
            rule.setMaxAmount(behavior.getDisposableIncome()
                    .multiply(BigDecimal.valueOf(0.3))); // Max 30% of disposable income
            rule.setMinAmount(BigDecimal.valueOf(5)); // Minimum $5

        } catch (Exception e) {
            log.warn("Failed to optimize rule parameters", e);
        }
    }

    /**
     * Analyze user's financial behavior for optimization
     */
    private FinancialBehavior analyzeFinancialBehavior(UUID userId) {
        try {
            // Get recent transactions
            List<Transaction> recentTransactions = transactionService
                    .getRecentTransactions(userId, 90); // Last 90 days

            // Analyze spending patterns
            SpendingPattern pattern = patternAnalyzer.analyzePattern(recentTransactions);

            // Detect income patterns
            IncomePattern incomePattern = incomeService.detectIncomePattern(userId);

            // Calculate key metrics
            BigDecimal monthlyIncome = incomePattern.getAverageMonthlyIncome();
            BigDecimal monthlyExpenses = pattern.getAverageMonthlySpending();
            BigDecimal disposableIncome = monthlyIncome.subtract(monthlyExpenses);

            return FinancialBehavior.builder()
                    .monthlyIncome(monthlyIncome)
                    .monthlyExpenses(monthlyExpenses)
                    .disposableIncome(disposableIncome)
                    .savingsRate(calculateSavingsRate(monthlyIncome, monthlyExpenses))
                    .incomeFrequency(incomePattern.getFrequency())
                    .incomeStability(incomePattern.getStability())
                    .spendingPattern(pattern)
                    .expenseVolatility(pattern.getVolatility())
                    .primarySpendingCategories(pattern.getTopCategories())
                    .build();

        } catch (Exception e) {
            log.warn("Failed to analyze financial behavior for user: {}", userId, e);
            return FinancialBehavior.getDefault();
        }
    }

    // Helper methods and placeholder implementations

    private BigDecimal applyRuleLimits(AutoSaveRule rule, BigDecimal amount) {
        if (rule.getMaxAmount() != null && amount.compareTo(rule.getMaxAmount()) > 0) {
            amount = rule.getMaxAmount();
        }
        if (rule.getMinAmount() != null && amount.compareTo(rule.getMinAmount()) < 0) {
            amount = rule.getMinAmount();
        }
        return amount;
    }

    private void publishAutoSaveEvent(AutoSaveRule rule, SavingsGoal goal, BigDecimal amount) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("ruleId", rule.getId());
            event.put("goalId", goal.getId());
            event.put("userId", rule.getUserId());
            event.put("amount", amount);
            event.put("ruleType", rule.getRuleType());
            event.put("timestamp", LocalDateTime.now());

            kafkaTemplate.send("auto-save-executed", rule.getId().toString(), event);
        } catch (Exception e) {
            log.warn("Failed to publish auto-save event", e);
        }
    }

    private LocalDateTime calculateNextExecutionTime(AutoSaveRule rule) {
        LocalDateTime now = LocalDateTime.now();
        
        return switch (rule.getFrequency()) {
            case DAILY -> now.plusDays(1);
            case WEEKLY -> now.plusWeeks(1);
            case BIWEEKLY -> now.plusWeeks(2);
            case MONTHLY -> now.plusMonths(1);
            case QUARTERLY -> now.plusMonths(3);
            case ANNUALLY -> now.plusYears(1);
            default -> now.plusDays(1);
        };
    }

    // Additional helper classes
    
    private static class AutoSaveExecution {
        private final boolean successful;
        private final BigDecimal amount;
        private final UUID contributionId;
        private final String error;

        private AutoSaveExecution(boolean successful, BigDecimal amount, UUID contributionId, String error) {
            this.successful = successful;
            this.amount = amount;
            this.contributionId = contributionId;
            this.error = error;
        }

        public static AutoSaveExecution success(BigDecimal amount, UUID contributionId) {
            return new AutoSaveExecution(true, amount, contributionId, null);
        }

        public static AutoSaveExecution failure(String error) {
            return new AutoSaveExecution(false, BigDecimal.ZERO, null, error);
        }

        public boolean isSuccessful() { return successful; }
        public BigDecimal getAmount() { return amount; }
        public UUID getContributionId() { return contributionId; }
        public String getError() { return error; }
    }

    private static class FinancialBehavior {
        private BigDecimal monthlyIncome;
        private BigDecimal monthlyExpenses;
        private BigDecimal disposableIncome;
        private BigDecimal savingsRate;
        private String incomeFrequency;
        private Double incomeStability;
        private SpendingPattern spendingPattern;
        private Double expenseVolatility;
        private List<String> primarySpendingCategories;

        public static FinancialBehaviorBuilder builder() {
            return new FinancialBehaviorBuilder();
        }

        public static FinancialBehavior getDefault() {
            return new FinancialBehavior();
        }

        // Getters
        public BigDecimal getDisposableIncome() { return disposableIncome; }
        public BigDecimal getSavingsRate() { return savingsRate; }
        public Double getExpenseVolatility() { return expenseVolatility; }
        public String getIncomeFrequency() { return incomeFrequency; }
        public SpendingPattern getSpendingPattern() { return spendingPattern; }

        public static class FinancialBehaviorBuilder {
            private BigDecimal monthlyIncome;
            private BigDecimal monthlyExpenses;
            private BigDecimal disposableIncome;
            private BigDecimal savingsRate;
            private String incomeFrequency;
            private Double incomeStability;
            private SpendingPattern spendingPattern;
            private Double expenseVolatility;
            private List<String> primarySpendingCategories;

            public FinancialBehaviorBuilder monthlyIncome(BigDecimal income) {
                this.monthlyIncome = income;
                return this;
            }

            public FinancialBehaviorBuilder monthlyExpenses(BigDecimal expenses) {
                this.monthlyExpenses = expenses;
                return this;
            }

            public FinancialBehaviorBuilder disposableIncome(BigDecimal income) {
                this.disposableIncome = income;
                return this;
            }

            public FinancialBehaviorBuilder savingsRate(BigDecimal rate) {
                this.savingsRate = rate;
                return this;
            }

            public FinancialBehaviorBuilder incomeFrequency(String frequency) {
                this.incomeFrequency = frequency;
                return this;
            }

            public FinancialBehaviorBuilder incomeStability(Double stability) {
                this.incomeStability = stability;
                return this;
            }

            public FinancialBehaviorBuilder spendingPattern(SpendingPattern pattern) {
                this.spendingPattern = pattern;
                return this;
            }

            public FinancialBehaviorBuilder expenseVolatility(Double volatility) {
                this.expenseVolatility = volatility;
                return this;
            }

            public FinancialBehaviorBuilder primarySpendingCategories(List<String> categories) {
                this.primarySpendingCategories = categories;
                return this;
            }

            public FinancialBehavior build() {
                FinancialBehavior behavior = new FinancialBehavior();
                behavior.monthlyIncome = this.monthlyIncome;
                behavior.monthlyExpenses = this.monthlyExpenses;
                behavior.disposableIncome = this.disposableIncome;
                behavior.savingsRate = this.savingsRate;
                behavior.incomeFrequency = this.incomeFrequency;
                behavior.incomeStability = this.incomeStability;
                behavior.spendingPattern = this.spendingPattern;
                behavior.expenseVolatility = this.expenseVolatility;
                behavior.primarySpendingCategories = this.primarySpendingCategories;
                return behavior;
            }
        }
    }
}
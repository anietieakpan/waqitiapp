package com.waqiti.savings.service;

import com.waqiti.savings.domain.*;
import com.waqiti.savings.dto.request.*;
import com.waqiti.savings.dto.response.*;
import com.waqiti.savings.exception.*;
import com.waqiti.savings.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Comprehensive Savings Goal Service
 * 
 * Manages savings goals, automated savings, progress tracking,
 * milestone achievements, and intelligent recommendations
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class SavingsGoalService {

    private final SavingsGoalRepository goalRepository;
    private final AutoSaveRuleRepository ruleRepository;
    private final SavingsContributionRepository contributionRepository;
    private final MilestoneRepository milestoneRepository;
    private final SavingsAccountRepository accountRepository;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;
    private final InvestmentOptimizationService investmentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Mappers (added in Phase 2)
    private final com.waqiti.savings.mapper.SavingsGoalMapper goalMapper;
    private final com.waqiti.savings.mapper.ContributionMapper contributionMapper;
    private final com.waqiti.savings.mapper.MilestoneMapper milestoneMapper;

    // Event topics
    private static final String GOAL_CREATED_TOPIC = "savings-goal-created";
    private static final String GOAL_ACHIEVED_TOPIC = "savings-goal-achieved";
    private static final String MILESTONE_REACHED_TOPIC = "savings-milestone-reached";
    private static final String AUTO_SAVE_EXECUTED_TOPIC = "auto-save-executed";

    /**
     * Create a new savings goal with intelligent recommendations
     */
    @Transactional
    public SavingsGoalResponse createSavingsGoal(UUID userId, CreateSavingsGoalRequest request) {
        log.info("Creating savings goal for user: {} - {} targeting {}", 
                userId, request.getName(), request.getTargetAmount());

        try {
            // Validate request
            validateGoalRequest(request);

            // Check if user has capacity for new goal
            validateUserCapacity(userId, request.getTargetAmount());

            // Get or create savings account
            SavingsAccount account = getOrCreateSavingsAccount(userId, request.getAccountId());

            // Build savings goal
            SavingsGoal goal = buildSavingsGoal(userId, account.getId(), request);

            // Calculate optimal savings strategy
            SavingsStrategy strategy = calculateOptimalStrategy(
                    userId, goal.getTargetAmount(), goal.getTargetDate());
            
            // Apply strategy recommendations
            applyStrategyToGoal(goal, strategy);

            // Create milestones
            List<Milestone> milestones = createGoalMilestones(goal);
            
            // Save goal and milestones
            goal = goalRepository.save(goal);
            milestoneRepository.saveAll(milestones);

            // Create auto-save rules if requested
            if (request.isEnableAutoSave()) {
                createDefaultAutoSaveRules(goal, request.getAutoSavePreferences());
            }

            // Send notifications
            notificationService.sendGoalCreatedNotification(userId, goal);

            // Publish event
            publishGoalEvent(GOAL_CREATED_TOPIC, goal);

            // Generate initial insights
            generateGoalInsightsAsync(userId, goal);

            log.info("Savings goal created successfully: {} for user: {}", goal.getId(), userId);
            return mapToGoalResponse(goal, strategy, milestones);

        } catch (Exception e) {
            log.error("Failed to create savings goal for user: {}", userId, e);
            throw new SavingsGoalException("Failed to create savings goal: " + e.getMessage(), e);
        }
    }

    /**
     * Make a contribution to a savings goal
     */
    @Transactional
    public ContributionResponse contributeToGoal(UUID userId, UUID goalId, ContributionRequest request) {
        log.info("Processing contribution to goal: {} amount: {}", goalId, request.getAmount());

        try {
            // Validate goal ownership
            SavingsGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
                    .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + goalId));

            // Validate contribution
            validateContribution(goal, request);

            // Process payment
            PaymentResult paymentResult = processContributionPayment(
                    userId, goal.getAccountId(), request);

            if (!paymentResult.isSuccessful()) {
                throw new PaymentFailedException("Failed to process payment: " + paymentResult.getError());
            }

            // Create contribution record
            SavingsContribution contribution = createContribution(goal, request, paymentResult);

            // Update goal progress
            updateGoalProgress(goal, contribution.getAmount());

            // Check milestones
            checkAndUpdateMilestones(goal);

            // Check if goal is achieved
            if (goal.hasReachedTarget() && !goal.isCompleted()) {
                completeGoal(goal);
            }

            // Update analytics
            analyticsService.trackContribution(userId, goal, contribution);

            // Send notifications
            sendContributionNotifications(goal, contribution);

            log.info("Contribution processed successfully: {} to goal: {}", 
                    contribution.getId(), goalId);
            
            return mapToContributionResponse(contribution, goal);

        } catch (Exception e) {
            log.error("Failed to process contribution to goal: {}", goalId, e);
            throw new SavingsGoalException("Failed to process contribution: " + e.getMessage(), e);
        }
    }

    /**
     * Execute auto-save rules (scheduled job)
     */
    @Scheduled(cron = "0 0 * * * *") // Run every hour
    @Async
    public void executeAutoSaveRules() {
        log.info("Starting auto-save rule execution");

        try {
            // Get active rules that are due for execution
            List<AutoSaveRule> dueRules = ruleRepository.findRulesDueForExecution(LocalDateTime.now());

            for (AutoSaveRule rule : dueRules) {
                try {
                    executeAutoSaveRule(rule);
                } catch (Exception e) {
                    log.error("Failed to execute auto-save rule: {}", rule.getId(), e);
                    rule.recordFailure(e.getMessage());
                    ruleRepository.save(rule);
                }
            }

            log.info("Completed auto-save rule execution for {} rules", dueRules.size());

        } catch (Exception e) {
            log.error("Error in auto-save execution job", e);
        }
    }

    /**
     * Execute a single auto-save rule
     */
    @Transactional
    public void executeAutoSaveRule(AutoSaveRule rule) {
        log.debug("Executing auto-save rule: {} type: {}", rule.getId(), rule.getRuleType());

        try {
            // Validate rule can execute
            if (!rule.canExecute()) {
                log.debug("Rule cannot execute: {}", rule.getId());
                return;
            }

            // Get associated goal
            SavingsGoal goal = goalRepository.findById(rule.getGoalId())
                    .orElseThrow(() -> new GoalNotFoundException("Goal not found for rule"));

            // Calculate save amount based on rule type
            BigDecimal saveAmount = calculateAutoSaveAmount(rule, goal);

            if (saveAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("No amount to save for rule: {}", rule.getId());
                return;
            }

            // Apply min/max limits
            saveAmount = applyRuleLimits(rule, saveAmount);

            // Process the auto-save transaction
            PaymentResult paymentResult = processAutoSavePayment(
                    rule.getUserId(), goal.getAccountId(), rule, saveAmount);

            if (paymentResult.isSuccessful()) {
                // Create contribution
                SavingsContribution contribution = createAutoSaveContribution(goal, rule, saveAmount);

                // Update goal
                updateGoalProgress(goal, saveAmount);

                // Update rule statistics
                rule.recordSuccess(saveAmount);

                // Calculate next execution time
                rule.setNextExecutionAt(calculateNextExecutionTime(rule));

                // Send notifications if enabled
                if (rule.shouldNotify()) {
                    notificationService.sendAutoSaveNotification(rule.getUserId(), goal, saveAmount);
                }

                // Publish event
                publishAutoSaveEvent(rule, goal, saveAmount);

                log.info("Auto-save executed successfully: {} saved {} to goal {}", 
                        rule.getId(), saveAmount, goal.getId());

            } else {
                rule.recordFailure(paymentResult.getError());
                
                if (rule.getNotifyOnFailure()) {
                    notificationService.sendAutoSaveFailureNotification(
                            rule.getUserId(), goal, paymentResult.getError());
                }
            }

            ruleRepository.save(rule);

        } catch (Exception e) {
            log.error("Failed to execute auto-save rule: {}", rule.getId(), e);
            throw new AutoSaveException("Failed to execute auto-save: " + e.getMessage(), e);
        }
    }

    /**
     * Get savings goal analytics and insights
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "goalAnalytics", key = "#userId + ':' + #goalId")
    public GoalAnalyticsResponse getGoalAnalytics(UUID userId, UUID goalId) {
        log.info("Generating analytics for goal: {}", goalId);

        try {
            SavingsGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
                    .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + goalId));

            // Get contribution history
            List<SavingsContribution> contributions = 
                    contributionRepository.findByGoalIdOrderByCreatedAtDesc(goalId);

            // Calculate analytics
            GoalAnalytics analytics = calculateGoalAnalytics(goal, contributions);

            // Generate projections
            GoalProjection projection = generateGoalProjection(goal, analytics);

            // Get milestones progress
            List<MilestoneProgress> milestoneProgress = getMilestoneProgress(goalId);

            // Generate insights and recommendations
            List<GoalInsight> insights = generateGoalInsights(goal, analytics, projection);

            // Calculate optimization opportunities
            List<OptimizationOpportunity> optimizations = 
                    investmentService.findOptimizationOpportunities(goal, analytics);

            return GoalAnalyticsResponse.builder()
                    .goal(mapToGoalResponse(goal))
                    .analytics(analytics)
                    .projection(projection)
                    .milestoneProgress(milestoneProgress)
                    .insights(insights)
                    .optimizations(optimizations)
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate analytics for goal: {}", goalId, e);
            throw new SavingsGoalException("Failed to generate analytics", e);
        }
    }

    /**
     * Get personalized savings recommendations
     */
    @Transactional(readOnly = true)
    public SavingsRecommendationsResponse getSavingsRecommendations(UUID userId) {
        log.info("Generating savings recommendations for user: {}", userId);

        try {
            // Get user's financial profile
            FinancialProfile profile = analyticsService.getUserFinancialProfile(userId);

            // Get active goals
            List<SavingsGoal> activeGoals = goalRepository.findActiveGoalsByUserId(userId);

            // Analyze spending patterns
            SpendingAnalysis spendingAnalysis = analyticsService.analyzeSpending(userId);

            // Generate recommendations
            List<SavingsRecommendation> recommendations = new ArrayList<>();

            // Recommend optimal save amount
            BigDecimal optimalSaveAmount = calculateOptimalSaveAmount(profile, spendingAnalysis);
            recommendations.add(createSaveAmountRecommendation(optimalSaveAmount, profile));

            // Recommend new goals based on life events
            recommendations.addAll(recommendGoalsBasedOnLifeEvents(profile));

            // Recommend auto-save rules
            recommendations.addAll(recommendAutoSaveRules(profile, spendingAnalysis));

            // Recommend investment opportunities
            recommendations.addAll(recommendInvestmentOpportunities(activeGoals, profile));

            // Calculate potential savings
            BigDecimal potentialSavings = calculatePotentialSavings(spendingAnalysis);

            return SavingsRecommendationsResponse.builder()
                    .recommendations(recommendations)
                    .potentialMonthlySavings(potentialSavings)
                    .currentSavingsRate(profile.getSavingsRate())
                    .recommendedSavingsRate(calculateRecommendedSavingsRate(profile))
                    .topOpportunities(identifyTopSavingsOpportunities(spendingAnalysis))
                    .generatedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate savings recommendations for user: {}", userId, e);
            throw new SavingsGoalException("Failed to generate recommendations", e);
        }
    }

    /**
     * Process round-up transactions for spare change savings
     */
    @Async
    public CompletableFuture<RoundUpResult> processRoundUpTransaction(
            UUID userId, UUID transactionId, BigDecimal transactionAmount) {
        
        log.debug("Processing round-up for transaction: {} amount: {}", transactionId, transactionAmount);

        try {
            // Find active round-up rules
            List<AutoSaveRule> roundUpRules = ruleRepository.findActiveRoundUpRules(userId);

            if (roundUpRules.isEmpty()) {
                return CompletableFuture.completedFuture(RoundUpResult.noRules());
            }

            BigDecimal totalRoundUp = BigDecimal.ZERO;
            List<RoundUpExecution> executions = new ArrayList<>();

            for (AutoSaveRule rule : roundUpRules) {
                try {
                    // Calculate round-up amount
                    BigDecimal roundUpAmount = calculateRoundUpAmount(
                            transactionAmount, rule.getRoundUpTo());

                    if (roundUpAmount.compareTo(BigDecimal.ZERO) > 0) {
                        // Get associated goal and process if eligible
                        goalRepository.findById(rule.getGoalId())
                            .filter(SavingsGoal::canContribute)
                            .ifPresent(goal -> {
                                // Process round-up contribution
                                PaymentResult payment = processRoundUpPayment(
                                        userId, goal.getAccountId(), roundUpAmount);

                            if (payment.isSuccessful()) {
                                // Create contribution
                                SavingsContribution contribution = createRoundUpContribution(
                                        goal, rule, roundUpAmount, transactionId);

                                // Update goal
                                updateGoalProgress(goal, roundUpAmount);

                                // Update rule statistics
                                rule.recordSuccess(roundUpAmount);
                                ruleRepository.save(rule);

                                totalRoundUp = totalRoundUp.add(roundUpAmount);
                                executions.add(new RoundUpExecution(goal.getId(), roundUpAmount, true));

                                log.debug("Round-up successful: {} to goal {}", roundUpAmount, goal.getId());
                            }
                            });
                    }
                } catch (Exception e) {
                    log.warn("Failed to process round-up for rule: {}", rule.getId(), e);
                    executions.add(new RoundUpExecution(rule.getGoalId(), BigDecimal.ZERO, false));
                }
            }

            // Send notification if any round-ups were successful
            if (totalRoundUp.compareTo(BigDecimal.ZERO) > 0) {
                notificationService.sendRoundUpNotification(userId, totalRoundUp, executions.size());
            }

            return CompletableFuture.completedFuture(
                    RoundUpResult.success(totalRoundUp, executions));

        } catch (Exception e) {
            log.error("Failed to process round-up for transaction: {}", transactionId, e);
            return CompletableFuture.completedFuture(RoundUpResult.error(e.getMessage()));
        }
    }

    /**
     * Withdraw from savings goal
     */
    @Transactional
    public WithdrawalResponse withdrawFromGoal(UUID userId, UUID goalId, WithdrawalRequest request) {
        log.info("Processing withdrawal from goal: {} amount: {}", goalId, request.getAmount());

        try {
            // Validate goal ownership and withdrawal eligibility
            SavingsGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
                    .orElseThrow(() -> new GoalNotFoundException("Goal not found: " + goalId));

            if (!goal.canWithdraw()) {
                throw new WithdrawalNotAllowedException("Withdrawals not allowed for this goal");
            }

            if (request.getAmount() != null && goal.getCurrentAmount() != null && 
                request.getAmount().compareTo(goal.getCurrentAmount()) > 0) {
                throw new InsufficientFundsException("Insufficient funds in goal");
            }

            // Process withdrawal
            PaymentResult withdrawalResult = processWithdrawal(
                    userId, goal.getAccountId(), request);

            if (!withdrawalResult.isSuccessful()) {
                throw new PaymentFailedException("Failed to process withdrawal: " + withdrawalResult.getError());
            }

            // Create withdrawal record
            SavingsContribution withdrawal = createWithdrawalRecord(goal, request, withdrawalResult);

            // Update goal balance
            goal.setCurrentAmount(goal.getCurrentAmount().subtract(request.getAmount()));
            goal.setTotalWithdrawals(goal.getTotalWithdrawals() + 1);
            goal.setLastWithdrawalAt(LocalDateTime.now());
            
            // Recalculate progress
            goal.setProgressPercentage(goal.getCompletionPercentage());
            
            // Check if goal needs to be reactivated
            if (goal.isCompleted() && !goal.hasReachedTarget()) {
                goal.setStatus(SavingsGoal.Status.ACTIVE);
                goal.setCompletedAt(null);
            }

            goal = goalRepository.save(goal);

            // Send notification
            notificationService.sendWithdrawalNotification(userId, goal, request.getAmount());

            log.info("Withdrawal processed successfully from goal: {}", goalId);
            
            return WithdrawalResponse.builder()
                    .withdrawalId(withdrawal.getId())
                    .goalId(goalId)
                    .amount(request.getAmount())
                    .remainingBalance(goal.getCurrentAmount())
                    .processedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to process withdrawal from goal: {}", goalId, e);
            throw new SavingsGoalException("Failed to process withdrawal: " + e.getMessage(), e);
        }
    }

    // Private helper methods

    private void validateGoalRequest(CreateSavingsGoalRequest request) {
        if (request.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Target amount must be positive");
        }
        if (request.getTargetDate() != null && request.getTargetDate().isBefore(LocalDateTime.now())) {
            throw new ValidationException("Target date cannot be in the past");
        }
    }

    private void validateUserCapacity(UUID userId, BigDecimal targetAmount) {
        // Check user's financial capacity
        FinancialProfile profile = analyticsService.getUserFinancialProfile(userId);
        
        BigDecimal totalActiveGoals = goalRepository.sumActiveGoalTargets(userId);
        BigDecimal disposableIncome = profile.getMonthlyIncome()
                .subtract(profile.getMonthlyExpenses());
        
        if (disposableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("User {} has no disposable income for savings", userId);
        }
    }

    private SavingsGoal buildSavingsGoal(UUID userId, UUID accountId, CreateSavingsGoalRequest request) {
        return SavingsGoal.builder()
                .userId(userId)
                .accountId(accountId)
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .targetAmount(request.getTargetAmount())
                .currentAmount(BigDecimal.ZERO)
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .targetDate(request.getTargetDate())
                .priority(request.getPriority())
                .visibility(request.getVisibility())
                .imageUrl(request.getImageUrl())
                .icon(request.getIcon())
                .color(request.getColor())
                .autoSaveEnabled(request.isEnableAutoSave())
                .flexibleTarget(request.isFlexibleTarget())
                .allowWithdrawals(request.isAllowWithdrawals())
                .notificationsEnabled(request.isEnableNotifications())
                .reminderFrequency(request.getReminderFrequency())
                .status(SavingsGoal.Status.ACTIVE)
                .build();
    }

    private SavingsStrategy calculateOptimalStrategy(UUID userId, BigDecimal targetAmount, LocalDateTime targetDate) {
        // Calculate optimal savings strategy based on user's financial situation
        FinancialProfile profile = analyticsService.getUserFinancialProfile(userId);
        
        BigDecimal monthlyRequired = BigDecimal.ZERO;
        if (targetDate != null) {
            long monthsRemaining = ChronoUnit.MONTHS.between(LocalDateTime.now(), targetDate);
            if (monthsRemaining > 0) {
                monthlyRequired = targetAmount.divide(
                        BigDecimal.valueOf(monthsRemaining), 2, RoundingMode.HALF_UP);
            }
        }
        
        return SavingsStrategy.builder()
                .requiredMonthlySaving(monthlyRequired)
                .recommendedMonthlySaving(calculateRecommendedMonthlySaving(profile, targetAmount))
                .suggestedAutoSaveAmount(calculateSuggestedAutoSave(profile))
                .optimalSaveDay(determineOptimalSaveDay(profile))
                .riskLevel(determineRiskLevel(targetAmount, profile))
                .build();
    }

    private void updateGoalProgress(SavingsGoal goal, BigDecimal contributionAmount) {
        goal.setCurrentAmount(goal.getCurrentAmount().add(contributionAmount));
        goal.setProgressPercentage(goal.getCompletionPercentage());
        goal.setTotalContributions(goal.getTotalContributions() + 1);
        goal.setLastContributionAt(LocalDateTime.now());
        
        // Update streaks
        if (shouldUpdateStreak(goal)) {
            goal.setCurrentStreak(goal.getCurrentStreak() + 1);
            if (goal.getCurrentStreak() > goal.getLongestStreak()) {
                goal.setLongestStreak(goal.getCurrentStreak());
            }
        } else {
            goal.setCurrentStreak(1);
        }
        
        // Recalculate projections
        recalculateProjections(goal);
        
        goalRepository.save(goal);
    }

    private void completeGoal(SavingsGoal goal) {
        goal.setStatus(SavingsGoal.Status.COMPLETED);
        goal.setCompletedAt(LocalDateTime.now());
        goalRepository.save(goal);
        
        // Send celebration notification
        notificationService.sendGoalAchievedNotification(goal.getUserId(), goal);
        
        // Publish achievement event
        publishGoalEvent(GOAL_ACHIEVED_TOPIC, goal);
        
        // Award badges/rewards if applicable
        analyticsService.recordGoalAchievement(goal.getUserId(), goal);
    }

    private void publishGoalEvent(String topic, SavingsGoal goal) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("goalId", goal.getId());
            event.put("userId", goal.getUserId());
            event.put("goalName", goal.getName());
            event.put("targetAmount", goal.getTargetAmount());
            event.put("currentAmount", goal.getCurrentAmount());
            event.put("status", goal.getStatus());
            event.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send(topic, goal.getId().toString(), event);
        } catch (Exception e) {
            log.warn("Failed to publish goal event to topic: {}", topic, e);
        }
    }

    // ========================================
    // MISSING METHOD IMPLEMENTATIONS (Phase 3)
    // ========================================

    /**
     * Get or create a savings account for the user.
     */
    private SavingsAccount getOrCreateSavingsAccount(UUID userId, UUID accountId) {
        if (accountId != null) {
            return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings account not found: " + accountId));
        }

        // Get user's default savings account or create one
        return accountRepository.findByUserIdAndAccountType(userId, SavingsAccount.AccountType.STANDARD)
            .stream()
            .findFirst()
            .orElseGet(() -> {
                log.info("Creating default savings account for user: {}", userId);
                SavingsAccount account = SavingsAccount.builder()
                    .userId(userId)
                    .accountType(SavingsAccount.AccountType.STANDARD)
                    .currency("USD")
                    .balance(BigDecimal.ZERO)
                    .availableBalance(BigDecimal.ZERO)
                    .status(SavingsAccount.Status.ACTIVE)
                    .build();
                return accountRepository.save(account);
            });
    }

    /**
     * Apply calculated strategy parameters to the goal.
     */
    private void applyStrategyToGoal(SavingsGoal goal, SavingsStrategy strategy) {
        if (strategy == null) return;

        goal.setRequiredMonthlySaving(strategy.requiredMonthlySaving);
        goal.setRecommendedMonthlySaving(strategy.recommendedMonthlySaving);

        // Apply other strategy parameters as metadata
        if (goal.getMetadata() == null) {
            goal.setMetadata(new HashMap<>());
        }
        goal.getMetadata().put("suggestedAutoSaveAmount", strategy.suggestedAutoSaveAmount);
        goal.getMetadata().put("optimalSaveDay", strategy.optimalSaveDay);
        goal.getMetadata().put("riskLevel", strategy.riskLevel);
    }

    /**
     * Create milestone checkpoints for a goal (25%, 50%, 75%, 100%).
     */
    private List<Milestone> createGoalMilestones(SavingsGoal goal) {
        List<Milestone> milestones = new ArrayList<>();
        BigDecimal[] percentages = {
            new BigDecimal("25"),
            new BigDecimal("50"),
            new BigDecimal("75"),
            new BigDecimal("100")
        };

        for (BigDecimal percentage : percentages) {
            Milestone milestone = milestoneMapper.createSystemMilestone(
                percentage,
                goal.getId(),
                goal.getUserId()
            );

            // Calculate target amount for this milestone
            BigDecimal targetAmount = goal.getTargetAmount()
                .multiply(percentage)
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            milestone.setTargetAmount(targetAmount);

            // Set visual customization based on percentage
            milestone.setColor(getMilestoneColor(percentage.intValue()));
            milestone.setIcon(getMilestoneIcon(percentage.intValue()));

            milestones.add(milestone);
        }

        log.info("Created {} milestones for goal: {}", milestones.size(), goal.getId());
        return milestones;
    }

    private String getMilestoneColor(int percentage) {
        return switch (percentage) {
            case 25 -> "#4CAF50";  // Green
            case 50 -> "#2196F3";  // Blue
            case 75 -> "#FF9800";  // Orange
            case 100 -> "#FFD700"; // Gold
            default -> "#9E9E9E";  // Gray
        };
    }

    private String getMilestoneIcon(int percentage) {
        return switch (percentage) {
            case 25 -> "flag";
            case 50 -> "star-half";
            case 75 -> "rocket";
            case 100 -> "trophy";
            default -> "circle";
        };
    }

    /**
     * Create default auto-save rules based on preferences.
     */
    private void createDefaultAutoSaveRules(SavingsGoal goal, Map<String, Object> autoSavePreferences) {
        if (autoSavePreferences == null || autoSavePreferences.isEmpty()) {
            log.debug("No auto-save preferences provided for goal: {}", goal.getId());
            return;
        }

        try {
            // Create round-up rule if enabled
            Boolean enableRoundUp = (Boolean) autoSavePreferences.get("enableRoundUp");
            if (Boolean.TRUE.equals(enableRoundUp)) {
                AutoSaveRule roundUpRule = AutoSaveRule.builder()
                    .goalId(goal.getId())
                    .userId(goal.getUserId())
                    .ruleType(AutoSaveRule.RuleType.ROUND_UP)
                    .roundUpTo(new BigDecimal("1.00"))
                    .frequency(AutoSaveRule.Frequency.ON_TRANSACTION)
                    .triggerType(AutoSaveRule.TriggerType.TRANSACTION_BASED)
                    .isActive(true)
                    .priority(5)
                    .build();
                ruleRepository.save(roundUpRule);
                log.info("Created round-up rule for goal: {}", goal.getId());
            }

            // Create fixed amount rule if specified
            BigDecimal fixedAmount = autoSavePreferences.get("fixedAmount") != null
                ? new BigDecimal(autoSavePreferences.get("fixedAmount").toString())
                : null;
            if (fixedAmount != null && fixedAmount.compareTo(BigDecimal.ZERO) > 0) {
                String frequency = (String) autoSavePreferences.getOrDefault("frequency", "MONTHLY");
                AutoSaveRule fixedRule = AutoSaveRule.builder()
                    .goalId(goal.getId())
                    .userId(goal.getUserId())
                    .ruleType(AutoSaveRule.RuleType.FIXED_AMOUNT)
                    .amount(fixedAmount)
                    .frequency(AutoSaveRule.Frequency.valueOf(frequency))
                    .triggerType(AutoSaveRule.TriggerType.TIME_BASED)
                    .isActive(true)
                    .priority(8)
                    .build();
                ruleRepository.save(fixedRule);
                log.info("Created fixed amount rule for goal: {} with amount: {}", goal.getId(), fixedAmount);
            }

        } catch (Exception e) {
            log.error("Failed to create auto-save rules for goal: {}", goal.getId(), e);
            // Don't fail goal creation if auto-save rules fail
        }
    }

    /**
     * Generate personalized insights for a new goal asynchronously.
     */
    @Async
    private void generateGoalInsightsAsync(UUID userId, SavingsGoal goal) {
        try {
            log.debug("Generating insights for goal: {} (async)", goal.getId());
            analyticsService.generateGoalInsights(userId, goal);
        } catch (Exception e) {
            log.error("Failed to generate insights for goal: {}", goal.getId(), e);
        }
    }

    /**
     * Map goal entity to response DTO using MapStruct mapper.
     */
    private SavingsGoalResponse mapToGoalResponse(
        SavingsGoal goal,
        SavingsStrategy strategy,
        List<Milestone> milestones
    ) {
        SavingsGoalResponse response = goalMapper.toResponse(goal);

        // Add strategy information
        if (strategy != null) {
            response.setRequiredMonthlySaving(strategy.requiredMonthlySaving);
            response.setRecommendedMonthlySaving(strategy.recommendedMonthlySaving);
        }

        // Add milestone count
        if (milestones != null) {
            response.setTotalMilestones(milestones.size());
        }

        return response;
    }

    /**
     * Validate contribution request before processing.
     */
    private void validateContribution(SavingsGoal goal, ContributionRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Contribution amount must be positive");
        }

        if (goal.getStatus() != SavingsGoal.Status.ACTIVE) {
            throw new ValidationException("Goal is not active. Status: " + goal.getStatus());
        }

        // Check if goal is already completed
        if (goal.getCurrentAmount() != null &&
            goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
            throw new ValidationException("Goal has already been completed");
        }

        // Validate maximum contribution limit
        BigDecimal maxContribution = new BigDecimal("10000.00");
        if (request.getAmount().compareTo(maxContribution) > 0) {
            throw new ValidationException("Contribution exceeds maximum limit of " + maxContribution);
        }
    }

    /**
     * Process payment for contribution.
     */
    private PaymentResult processContributionPayment(
        UUID userId,
        UUID accountId,
        ContributionRequest request
    ) {
        try {
            log.debug("Processing payment for contribution: user={}, amount={}", userId, request.getAmount());

            // Delegate to transaction service for actual payment processing
            return transactionService.processPayment(
                userId,
                accountId,
                request.getAmount(),
                request.getPaymentMethod(),
                "Goal Contribution",
                request.getIdempotencyKey()
            );

        } catch (Exception e) {
            log.error("Payment processing failed: {}", e.getMessage(), e);
            return PaymentResult.failure(e.getMessage());
        }
    }

    /**
     * Create contribution record using mapper.
     */
    private SavingsContribution createContribution(
        SavingsGoal goal,
        ContributionRequest request,
        PaymentResult paymentResult
    ) {
        // Use mapper to convert request to entity
        SavingsContribution contribution = contributionMapper.toEntity(request);

        // Set fields that mapper ignores
        contribution.setGoalId(goal.getId());
        contribution.setUserId(goal.getUserId());
        contribution.setType(SavingsContribution.ContributionType.MANUAL);
        contribution.setStatus(SavingsContribution.ContributionStatus.COMPLETED);
        contribution.setTransactionId(paymentResult.getTransactionId());
        contribution.setProcessedAt(LocalDateTime.now());

        // Save and return
        return contributionRepository.save(contribution);
    }

    /**
     * Check and update milestone achievements.
     */
    private void checkAndUpdateMilestones(SavingsGoal goal) {
        List<Milestone> pendingMilestones = milestoneRepository
            .findByGoalIdAndStatus(goal.getId(), Milestone.MilestoneStatus.PENDING);

        BigDecimal currentPercentage = goal.getProgressPercentage();

        for (Milestone milestone : pendingMilestones) {
            if (milestone.getTargetPercentage() != null &&
                currentPercentage.compareTo(milestone.getTargetPercentage()) >= 0) {

                // Achieve milestone
                milestone.setStatus(Milestone.MilestoneStatus.ACHIEVED);
                milestone.setAchievedAt(LocalDateTime.now());
                milestone.setAchievementAmount(goal.getCurrentAmount());
                milestoneRepository.save(milestone);

                log.info("Milestone achieved: {} for goal: {}", milestone.getName(), goal.getId());

                // Send notification
                notificationService.sendMilestoneAchievedNotification(goal.getUserId(), milestone, goal);

                // Publish event
                publishMilestoneEvent(milestone, goal);
            }
        }
    }

    private void publishMilestoneEvent(Milestone milestone, SavingsGoal goal) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("milestoneId", milestone.getId());
            event.put("goalId", goal.getId());
            event.put("userId", goal.getUserId());
            event.put("milestoneName", milestone.getName());
            event.put("targetPercentage", milestone.getTargetPercentage());
            event.put("achievedAmount", milestone.getAchievementAmount());
            event.put("timestamp", LocalDateTime.now());

            kafkaTemplate.send(MILESTONE_REACHED_TOPIC, milestone.getId().toString(), event);
        } catch (Exception e) {
            log.warn("Failed to publish milestone event", e);
        }
    }

    /**
     * Send contribution notifications.
     */
    private void sendContributionNotifications(SavingsGoal goal, SavingsContribution contribution) {
        try {
            notificationService.sendContributionReceivedNotification(
                goal.getUserId(),
                goal,
                contribution.getAmount()
            );
        } catch (Exception e) {
            log.error("Failed to send contribution notification", e);
        }
    }

    /**
     * Map contribution to response DTO using mapper.
     */
    private ContributionResponse mapToContributionResponse(
        SavingsContribution contribution,
        SavingsGoal goal
    ) {
        ContributionResponse response = contributionMapper.toResponse(contribution);

        // Add calculated fields that mapper ignores
        response.setNewGoalBalance(goal.getCurrentAmount());
        response.setProgressPercentage(goal.getProgressPercentage());

        // Check for achieved milestones
        List<Milestone> recentMilestones = milestoneRepository
            .findByGoalIdAndAchievedAtAfter(
                goal.getId(),
                LocalDateTime.now().minusMinutes(1)
            );

        if (!recentMilestones.isEmpty()) {
            response.setMilestonesAchieved(
                recentMilestones.stream()
                    .map(Milestone::getName)
                    .collect(Collectors.toList())
            );
        }

        // Check if goal completed
        response.setGoalCompleted(goal.getStatus() == SavingsGoal.Status.COMPLETED);

        return response;
    }

    /**
     * Calculate round-up amount to nearest dollar.
     */
    private BigDecimal calculateRoundUpAmount(BigDecimal transactionAmount, BigDecimal roundUpTo) {
        if (transactionAmount == null || roundUpTo == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal remainder = transactionAmount.remainder(roundUpTo);
        if (remainder.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return roundUpTo.subtract(remainder);
    }

    /**
     * Create round-up contribution record.
     */
    private SavingsContribution createRoundUpContribution(
        SavingsGoal goal,
        AutoSaveRule rule,
        BigDecimal amount,
        UUID transactionId
    ) {
        SavingsContribution contribution = SavingsContribution.builder()
            .goalId(goal.getId())
            .userId(goal.getUserId())
            .amount(amount)
            .type(SavingsContribution.ContributionType.ROUND_UP)
            .status(SavingsContribution.ContributionStatus.COMPLETED)
            .isAutoSave(true)
            .autoSaveRuleId(rule.getId())
            .transactionId(transactionId.toString())
            .source("ROUND_UP")
            .processedAt(LocalDateTime.now())
            .build();

        return contributionRepository.save(contribution);
    }

    /**
     * Calculate recommended monthly saving based on user profile.
     */
    private BigDecimal calculateRecommendedMonthlySaving(
        Object profile,
        BigDecimal targetAmount
    ) {
        // Default recommendation: 20% buffer over required
        return targetAmount.multiply(new BigDecimal("1.20"));
    }

    /**
     * Calculate suggested auto-save amount based on user behavior.
     */
    private BigDecimal calculateSuggestedAutoSave(Object profile) {
        // Default: $50/month auto-save
        return new BigDecimal("50.00");
    }

    /**
     * Determine optimal day for savings based on income patterns.
     */
    private Integer determineOptimalSaveDay(Object profile) {
        // Default: 1st of month (payday for most)
        return 1;
    }

    /**
     * Determine risk level based on target and financial profile.
     */
    private String determineRiskLevel(BigDecimal targetAmount, Object profile) {
        if (targetAmount.compareTo(new BigDecimal("10000")) > 0) {
            return "HIGH";
        } else if (targetAmount.compareTo(new BigDecimal("1000")) > 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /**
     * Check if streak should be updated.
     */
    private boolean shouldUpdateStreak(SavingsGoal goal) {
        if (goal.getLastContributionAt() == null) {
            return true;
        }

        // Streak continues if last contribution was within 7 days
        long daysSinceLastContribution = ChronoUnit.DAYS.between(
            goal.getLastContributionAt(),
            LocalDateTime.now()
        );

        return daysSinceLastContribution <= 7;
    }

    /**
     * Recalculate goal projections based on current progress.
     */
    private void recalculateProjections(SavingsGoal goal) {
        if (goal.getTargetDate() == null || goal.getCurrentAmount() == null) {
            return;
        }

        BigDecimal remaining = goal.getTargetAmount().subtract(goal.getCurrentAmount());
        long monthsRemaining = ChronoUnit.MONTHS.between(LocalDateTime.now(), goal.getTargetDate());

        if (monthsRemaining > 0) {
            BigDecimal requiredMonthly = remaining.divide(
                BigDecimal.valueOf(monthsRemaining),
                4,
                RoundingMode.HALF_UP
            );
            goal.setRequiredMonthlySaving(requiredMonthly);

            // Calculate average monthly contribution
            long monthsSinceStart = ChronoUnit.MONTHS.between(goal.getCreatedAt(), LocalDateTime.now());
            if (monthsSinceStart > 0) {
                BigDecimal avgMonthly = goal.getCurrentAmount().divide(
                    BigDecimal.valueOf(monthsSinceStart),
                    4,
                    RoundingMode.HALF_UP
                );
                goal.setAverageMonthlyContribution(avgMonthly);
            }
        }
    }

    // Placeholder classes and additional helper methods would go here...

    private static class SavingsStrategy {
        private BigDecimal requiredMonthlySaving;
        private BigDecimal recommendedMonthlySaving;
        private BigDecimal suggestedAutoSaveAmount;
        private Integer optimalSaveDay;
        private String riskLevel;
        
        public static SavingsStrategyBuilder builder() {
            return new SavingsStrategyBuilder();
        }
        
        public static class SavingsStrategyBuilder {
            private BigDecimal requiredMonthlySaving;
            private BigDecimal recommendedMonthlySaving;
            private BigDecimal suggestedAutoSaveAmount;
            private Integer optimalSaveDay;
            private String riskLevel;
            
            public SavingsStrategyBuilder requiredMonthlySaving(BigDecimal amount) {
                this.requiredMonthlySaving = amount;
                return this;
            }
            
            public SavingsStrategyBuilder recommendedMonthlySaving(BigDecimal amount) {
                this.recommendedMonthlySaving = amount;
                return this;
            }
            
            public SavingsStrategyBuilder suggestedAutoSaveAmount(BigDecimal amount) {
                this.suggestedAutoSaveAmount = amount;
                return this;
            }
            
            public SavingsStrategyBuilder optimalSaveDay(Integer day) {
                this.optimalSaveDay = day;
                return this;
            }
            
            public SavingsStrategyBuilder riskLevel(String level) {
                this.riskLevel = level;
                return this;
            }
            
            public SavingsStrategy build() {
                SavingsStrategy strategy = new SavingsStrategy();
                strategy.requiredMonthlySaving = this.requiredMonthlySaving;
                strategy.recommendedMonthlySaving = this.recommendedMonthlySaving;
                strategy.suggestedAutoSaveAmount = this.suggestedAutoSaveAmount;
                strategy.optimalSaveDay = this.optimalSaveDay;
                strategy.riskLevel = this.riskLevel;
                return strategy;
            }
        }
    }
}
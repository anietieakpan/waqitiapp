package com.waqiti.savings.service;

import com.waqiti.savings.domain.*;
import com.waqiti.savings.dto.request.*;
import com.waqiti.savings.dto.response.*;
import com.waqiti.savings.event.*;
import com.waqiti.savings.exception.*;
import com.waqiti.savings.repository.*;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.cache.DistributedLockService;
import com.waqiti.common.event.EventPublisher;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavingsGoalsService {

    private final SavingsGoalRepository goalRepository;
    private final SavingsAccountRepository accountRepository;
    private final ContributionRepository contributionRepository;
    private final AutoSaveRuleRepository autoSaveRuleRepository;
    private final MilestoneRepository milestoneRepository;
    private final GoalTemplateRepository templateRepository;
    private final SavingsAnalyticsRepository analyticsRepository;
    private final CacheService cacheService;
    private final DistributedLockService lockService;
    private final EventPublisher eventPublisher;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final InterestCalculationService interestService;
    private final GoalRecommendationService recommendationService;
    private final KYCClientService kycClientService;

    // Mappers (added in Phase 2)
    private final com.waqiti.savings.mapper.SavingsGoalMapper goalMapper;
    private final com.waqiti.savings.mapper.ContributionMapper contributionMapper;
    private final com.waqiti.savings.mapper.AutoSaveRuleMapper autoSaveRuleMapper;
    private final com.waqiti.savings.mapper.MilestoneMapper milestoneMapper;

    @Transactional
    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "SAVINGS_GOAL_CREATE")
    public SavingsGoalResponse createSavingsGoal(UUID userId, CreateSavingsGoalRequest request) {
        log.info("Creating savings goal for user: {} name: {} target: {}", 
                userId, request.getName(), request.getTargetAmount());

        // Validate goal parameters
        validateGoalRequest(request);

        // Enhanced KYC check for high-value savings goals
        if (request.getTargetAmount().compareTo(new BigDecimal("50000")) > 0) {
            if (!kycClientService.canUserMakeHighValueTransfer(userId.toString())) {
                throw new RuntimeException("Enhanced KYC verification required for savings goals over $50,000");
            }
        }

        // Get or create savings account
        SavingsAccount account = getOrCreateSavingsAccount(userId);

        // Create savings goal
        SavingsGoal goal = SavingsGoal.builder()
                .userId(userId)
                .accountId(account.getId())
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .targetAmount(request.getTargetAmount())
                .currentAmount(BigDecimal.ZERO)
                .currency(request.getCurrency())
                .targetDate(request.getTargetDate())
                .priority(request.getPriority())
                .visibility(request.getVisibility())
                .imageUrl(request.getImageUrl())
                .icon(request.getIcon())
                .color(request.getColor())
                .status(SavingsGoal.Status.ACTIVE)
                .autoSaveEnabled(request.getAutoSaveEnabled())
                .flexibleTarget(request.getFlexibleTarget())
                .allowWithdrawals(request.getAllowWithdrawals())
                .notificationsEnabled(request.getNotificationsEnabled())
                .metadata(request.getMetadata())
                .build();

        // Calculate savings metrics
        calculateGoalMetrics(goal);

        goal = goalRepository.save(goal);

        // Create milestones
        createDefaultMilestones(goal);

        // Set up auto-save rules if enabled
        if (Boolean.TRUE.equals(request.getAutoSaveEnabled()) && request.getAutoSaveRules() != null) {
            createAutoSaveRules(goal.getId(), request.getAutoSaveRules());
        }

        // Send welcome notification
        notificationService.sendGoalCreatedNotification(userId, goal);

        // Publish event
        eventPublisher.publish(SavingsGoalCreatedEvent.builder()
                .goalId(goal.getId())
                .userId(userId)
                .targetAmount(goal.getTargetAmount())
                .targetDate(goal.getTargetDate())
                .build());

        log.info("Savings goal created: {} for user: {}", goal.getId(), userId);
        
        return mapToSavingsGoalResponse(goal);
    }

    @Transactional
    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "SAVINGS_CONTRIBUTION")
    public ContributionResponse contributeToGoal(UUID userId, UUID goalId, ContributeRequest request) {
        log.info("Processing contribution to goal: {} amount: {}", goalId, request.getAmount());

        // Enhanced KYC check for large contributions
        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            if (!kycClientService.canUserMakeHighValueTransfer(userId.toString())) {
                throw new RuntimeException("Enhanced KYC verification required for contributions over $10,000");
            }
        }

        String lockKey = "goal-contribution:" + goalId;
        return lockService.executeWithLock(lockKey, Duration.ofMinutes(2), Duration.ofSeconds(30), () -> {

            SavingsGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
                    .orElseThrow(() -> new GoalNotFoundException("Savings goal not found"));

            if (goal.getStatus() != SavingsGoal.Status.ACTIVE) {
                throw new GoalNotActiveException("Cannot contribute to inactive goal");
            }

            // Validate contribution amount
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidContributionException("Contribution amount must be positive");
            }

            // Process payment
            PaymentResult paymentResult = paymentService.processContribution(
                    userId, request.getAmount(), request.getPaymentMethod(), goalId);

            if (!paymentResult.isSuccess()) {
                throw new PaymentFailedException("Payment processing failed: " + paymentResult.getErrorMessage());
            }

            // Create contribution record
            SavingsContribution contribution = SavingsContribution.builder()
                    .goalId(goalId)
                    .userId(userId)
                    .amount(request.getAmount())
                    .type(request.getType())
                    .paymentMethod(request.getPaymentMethod())
                    .transactionId(paymentResult.getTransactionId())
                    .status(SavingsContribution.Status.COMPLETED)
                    .source(request.getSource())
                    .note(request.getNote())
                    .isAutoSave(request.getIsAutoSave())
                    .build();

            contribution = contributionRepository.save(contribution);

            // Update goal progress
            goal.setCurrentAmount(goal.getCurrentAmount().add(request.getAmount()));
            goal.setLastContributionAt(LocalDateTime.now());
            goal.setTotalContributions(goal.getTotalContributions() + 1);
            
            // Check if goal is completed
            if (goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
                completeGoal(goal);
            } else {
                // Update progress percentage
                goal.setProgressPercentage(calculateProgressPercentage(goal));
                
                // Check milestones
                checkAndUpdateMilestones(goal);
            }

            // Recalculate metrics
            calculateGoalMetrics(goal);
            goalRepository.save(goal);

            // Update account balance
            updateAccountBalance(goal.getAccountId(), request.getAmount(), true);

            // Send notifications
            sendContributionNotifications(goal, contribution);

            // Publish event
            eventPublisher.publish(ContributionMadeEvent.builder()
                    .contributionId(contribution.getId())
                    .goalId(goalId)
                    .userId(userId)
                    .amount(request.getAmount())
                    .newTotal(goal.getCurrentAmount())
                    .progressPercentage(goal.getProgressPercentage())
                    .build());

            log.info("Contribution completed: {} to goal: {}", contribution.getId(), goalId);
            
            return mapToContributionResponse(contribution, goal);
        });
    }

    @Transactional
    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "SAVINGS_WITHDRAWAL")
    public WithdrawalResponse withdrawFromGoal(UUID userId, UUID goalId, WithdrawRequest request) {
        log.info("Processing withdrawal from goal: {} amount: {}", goalId, request.getAmount());

        // Enhanced KYC check for large withdrawals
        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            if (!kycClientService.canUserMakeHighValueTransfer(userId.toString())) {
                throw new RuntimeException("Enhanced KYC verification required for withdrawals over $10,000");
            }
        }

        String lockKey = "goal-withdrawal:" + goalId;
        return lockService.executeWithLock(lockKey, Duration.ofMinutes(2), Duration.ofSeconds(30), () -> {

            SavingsGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
                    .orElseThrow(() -> new GoalNotFoundException("Savings goal not found"));

            if (!goal.getAllowWithdrawals()) {
                throw new WithdrawalNotAllowedException("Withdrawals not allowed for this goal");
            }

            if (goal.getCurrentAmount().compareTo(request.getAmount()) < 0) {
                throw new InsufficientFundsException("Insufficient funds in savings goal");
            }

            // Process withdrawal
            PaymentResult withdrawalResult = paymentService.processWithdrawal(
                    userId, request.getAmount(), request.getDestination(), goalId);

            if (!withdrawalResult.isSuccess()) {
                throw new WithdrawalFailedException("Withdrawal processing failed");
            }

            // Create withdrawal record
            SavingsContribution withdrawal = SavingsContribution.builder()
                    .goalId(goalId)
                    .userId(userId)
                    .amount(request.getAmount().negate()) // Negative for withdrawal
                    .type(SavingsContribution.Type.WITHDRAWAL)
                    .transactionId(withdrawalResult.getTransactionId())
                    .status(SavingsContribution.Status.COMPLETED)
                    .withdrawalReason(request.getReason())
                    .note(request.getNote())
                    .build();

            contributionRepository.save(withdrawal);

            // Update goal
            goal.setCurrentAmount(goal.getCurrentAmount().subtract(request.getAmount()));
            goal.setTotalWithdrawals(goal.getTotalWithdrawals() + 1);
            goal.setLastWithdrawalAt(LocalDateTime.now());
            goal.setProgressPercentage(calculateProgressPercentage(goal));
            
            // Recalculate metrics
            calculateGoalMetrics(goal);
            goalRepository.save(goal);

            // Update account balance
            updateAccountBalance(goal.getAccountId(), request.getAmount(), false);

            // Send notifications
            notificationService.sendWithdrawalNotification(userId, goal, request.getAmount());

            // Publish event
            eventPublisher.publish(WithdrawalMadeEvent.builder()
                    .goalId(goalId)
                    .userId(userId)
                    .amount(request.getAmount())
                    .remainingAmount(goal.getCurrentAmount())
                    .reason(request.getReason())
                    .build());

            log.info("Withdrawal completed from goal: {} amount: {}", goalId, request.getAmount());
            
            return mapToWithdrawalResponse(withdrawal, goal);
        });
    }

    @Transactional
    public AutoSaveRuleResponse createAutoSaveRule(UUID userId, UUID goalId, CreateAutoSaveRuleRequest request) {
        log.info("Creating auto-save rule for goal: {} type: {}", goalId, request.getRuleType());

        SavingsGoal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new GoalNotFoundException("Savings goal not found"));

        if (!goal.getAutoSaveEnabled()) {
            throw new AutoSaveNotEnabledException("Auto-save not enabled for this goal");
        }

        // Validate rule parameters
        validateAutoSaveRule(request);

        // Create auto-save rule
        AutoSaveRule rule = AutoSaveRule.builder()
                .goalId(goalId)
                .userId(userId)
                .ruleType(request.getRuleType())
                .amount(request.getAmount())
                .percentage(request.getPercentage())
                .frequency(request.getFrequency())
                .dayOfWeek(request.getDayOfWeek())
                .dayOfMonth(request.getDayOfMonth())
                .triggerType(request.getTriggerType())
                .triggerConditions(request.getTriggerConditions())
                .maxAmount(request.getMaxAmount())
                .isActive(true)
                .priority(request.getPriority())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        rule = autoSaveRuleRepository.save(rule);

        // Schedule next execution
        scheduleAutoSaveExecution(rule);

        log.info("Auto-save rule created: {} for goal: {}", rule.getId(), goalId);
        
        return mapToAutoSaveRuleResponse(rule);
    }

    @Transactional(readOnly = true)
    public Page<SavingsGoalResponse> getUserGoals(UUID userId, GoalFilter filter, Pageable pageable) {
        Page<SavingsGoal> goals;
        
        if (filter != null && filter.getStatus() != null) {
            goals = goalRepository.findByUserIdAndStatus(userId, filter.getStatus(), pageable);
        } else if (filter != null && filter.getCategory() != null) {
            goals = goalRepository.findByUserIdAndCategory(userId, filter.getCategory(), pageable);
        } else {
            goals = goalRepository.findByUserIdOrderByPriorityDescCreatedAtDesc(userId, pageable);
        }

        return goals.map(this::mapToSavingsGoalResponse);
    }

    @Transactional(readOnly = true)
    public SavingsAnalyticsResponse getSavingsAnalytics(UUID userId, AnalyticsRequest request) {
        String cacheKey = cacheService.buildKey("savings-analytics", userId.toString(), 
                request.getStartDate().toString(), request.getEndDate().toString());
        
        SavingsAnalyticsResponse cached = cacheService.get(cacheKey, SavingsAnalyticsResponse.class);
        if (cached != null) {
            return cached;
        }

        // Calculate analytics
        List<SavingsGoal> activeGoals = goalRepository.findByUserIdAndStatus(userId, SavingsGoal.Status.ACTIVE);
        List<SavingsGoal> completedGoals = goalRepository.findByUserIdAndStatusAndCompletedAtBetween(
                userId, SavingsGoal.Status.COMPLETED, request.getStartDate(), request.getEndDate());

        BigDecimal totalSaved = activeGoals.stream()
                .map(SavingsGoal::getCurrentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTarget = activeGoals.stream()
                .map(SavingsGoal::getTargetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyContributions = calculateMonthlyContributions(userId, request);
        BigDecimal averageContribution = calculateAverageContribution(userId, request);
        
        List<ContributionTrend> contributionTrends = getContributionTrends(userId, request);
        List<GoalProgress> goalProgressList = activeGoals.stream()
                .map(this::mapToGoalProgress)
                .collect(Collectors.toList());

        SavingsAnalyticsResponse response = SavingsAnalyticsResponse.builder()
                .userId(userId)
                .totalSaved(totalSaved)
                .totalTarget(totalTarget)
                .overallProgress(totalTarget.compareTo(BigDecimal.ZERO) > 0 ? 
                        totalSaved.divide(totalTarget, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO)
                .activeGoalsCount(activeGoals.size())
                .completedGoalsCount(completedGoals.size())
                .monthlyContributions(monthlyContributions)
                .averageContribution(averageContribution)
                .contributionTrends(contributionTrends)
                .goalProgress(goalProgressList)
                .projectedCompletion(calculateProjectedCompletions(activeGoals))
                .savingsRate(calculateSavingsRate(userId, monthlyContributions))
                .generatedAt(LocalDateTime.now())
                .build();

        // Cache for 1 hour
        cacheService.set(cacheKey, response, Duration.ofHours(1));
        
        return response;
    }

    @Transactional
    public void executeScheduledAutoSaves() {
        log.info("Executing scheduled auto-saves");

        List<AutoSaveRule> dueRules = autoSaveRuleRepository.findDueRules(LocalDateTime.now());
        
        for (AutoSaveRule rule : dueRules) {
            try {
                executeAutoSaveRule(rule);
            } catch (Exception e) {
                log.error("Failed to execute auto-save rule: {} error: {}", rule.getId(), e.getMessage());
            }
        }

        log.info("Completed execution of {} auto-save rules", dueRules.size());
    }

    @Transactional
    public List<GoalRecommendationResponse> getGoalRecommendations(UUID userId) {
        log.info("Generating goal recommendations for user: {}", userId);

        // Get user's transaction patterns and financial profile
        UserFinancialProfile profile = getUserFinancialProfile(userId);
        
        // Get personalized recommendations
        List<GoalRecommendation> recommendations = recommendationService
                .generateRecommendations(profile);

        return recommendations.stream()
                .map(this::mapToGoalRecommendationResponse)
                .collect(Collectors.toList());
    }

    // Helper Methods

    private void validateGoalRequest(CreateSavingsGoalRequest request) {
        if (request.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidGoalException("Target amount must be positive");
        }

        if (request.getTargetDate() != null && request.getTargetDate().isBefore(LocalDateTime.now())) {
            throw new InvalidGoalException("Target date must be in the future");
        }

        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new InvalidGoalException("Goal name is required");
        }
    }

    private SavingsAccount getOrCreateSavingsAccount(UUID userId) {
        return accountRepository.findByUserIdAndType(userId, SavingsAccount.AccountType.SAVINGS)
                .orElseGet(() -> createDefaultSavingsAccount(userId));
    }

    private SavingsAccount createDefaultSavingsAccount(UUID userId) {
        SavingsAccount account = SavingsAccount.builder()
                .userId(userId)
                .accountType(SavingsAccount.AccountType.SAVINGS)
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .status(SavingsAccount.Status.ACTIVE)
                .interestRate(BigDecimal.valueOf(0.02)) // 2% APY default
                .build();

        return accountRepository.save(account);
    }

    private void calculateGoalMetrics(SavingsGoal goal) {
        // Calculate progress percentage
        goal.setProgressPercentage(calculateProgressPercentage(goal));

        // Calculate required monthly savings
        if (goal.getTargetDate() != null) {
            long monthsRemaining = ChronoUnit.MONTHS.between(LocalDateTime.now(), goal.getTargetDate());
            if (monthsRemaining > 0) {
                BigDecimal remainingAmount = goal.getTargetAmount().subtract(goal.getCurrentAmount());
                goal.setRequiredMonthlySaving(remainingAmount.divide(
                        BigDecimal.valueOf(monthsRemaining), 2, RoundingMode.HALF_UP));
            }
        }

        // Calculate projected completion
        if (goal.getAverageMonthlyContribution() != null && 
            goal.getAverageMonthlyContribution().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remainingAmount = goal.getTargetAmount().subtract(goal.getCurrentAmount());
            BigDecimal monthsToComplete = remainingAmount.divide(
                    goal.getAverageMonthlyContribution(), 0, RoundingMode.UP);
            goal.setProjectedCompletionDate(LocalDateTime.now().plusMonths(monthsToComplete.longValue()));
        }

        // Calculate streak
        goal.setCurrentStreak(calculateContributionStreak(goal.getId()));
    }

    private BigDecimal calculateProgressPercentage(SavingsGoal goal) {
        if (goal.getTargetAmount().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return goal.getCurrentAmount()
                .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .min(BigDecimal.valueOf(100));
    }

    private void createDefaultMilestones(SavingsGoal goal) {
        List<Integer> milestonePercentages = Arrays.asList(25, 50, 75, 100);
        
        for (Integer percentage : milestonePercentages) {
            Milestone milestone = Milestone.builder()
                    .goalId(goal.getId())
                    .name(percentage + "% of goal reached")
                    .targetPercentage(BigDecimal.valueOf(percentage))
                    .targetAmount(goal.getTargetAmount()
                            .multiply(BigDecimal.valueOf(percentage))
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                    .rewardType(percentage == 100 ? "BADGE" : "ENCOURAGEMENT")
                    .status(Milestone.Status.PENDING)
                    .build();

            milestoneRepository.save(milestone);
        }
    }

    private void completeGoal(SavingsGoal goal) {
        goal.setStatus(SavingsGoal.Status.COMPLETED);
        goal.setCompletedAt(LocalDateTime.now());
        goal.setProgressPercentage(BigDecimal.valueOf(100));

        // Calculate interest earned if applicable
        if (goal.getInterestRate() != null && goal.getInterestRate().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal interestEarned = interestService.calculateInterest(
                    goal.getCurrentAmount(), goal.getInterestRate(), goal.getCreatedAt(), goal.getCompletedAt());
            goal.setInterestEarned(interestEarned);
        }

        // Complete all pending milestones
        milestoneRepository.completeAllMilestones(goal.getId());

        // Send completion notification
        notificationService.sendGoalCompletedNotification(goal.getUserId(), goal);

        // Publish event
        eventPublisher.publish(GoalCompletedEvent.builder()
                .goalId(goal.getId())
                .userId(goal.getUserId())
                .targetAmount(goal.getTargetAmount())
                .actualAmount(goal.getCurrentAmount())
                .completedAt(goal.getCompletedAt())
                .build());
    }

    private void checkAndUpdateMilestones(SavingsGoal goal) {
        List<Milestone> pendingMilestones = milestoneRepository
                .findByGoalIdAndStatus(goal.getId(), Milestone.Status.PENDING);

        for (Milestone milestone : pendingMilestones) {
            if (goal.getProgressPercentage().compareTo(milestone.getTargetPercentage()) >= 0) {
                milestone.setStatus(Milestone.Status.ACHIEVED);
                milestone.setAchievedAt(LocalDateTime.now());
                milestoneRepository.save(milestone);

                // Send milestone notification
                notificationService.sendMilestoneAchievedNotification(goal.getUserId(), goal, milestone);
            }
        }
    }

    private void executeAutoSaveRule(AutoSaveRule rule) {
        try {
            BigDecimal amount = calculateAutoSaveAmount(rule);
            
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                // Create contribution request
                ContributeRequest request = ContributeRequest.builder()
                        .amount(amount)
                        .type(SavingsContribution.Type.AUTO_SAVE)
                        .paymentMethod(rule.getPaymentMethod())
                        .source("AUTO_SAVE_RULE_" + rule.getId())
                        .isAutoSave(true)
                        .build();

                contributeToGoal(rule.getUserId(), rule.getGoalId(), request);

                // Update rule execution
                rule.setLastExecutedAt(LocalDateTime.now());
                rule.setExecutionCount(rule.getExecutionCount() + 1);
                rule.setTotalSaved(rule.getTotalSaved().add(amount));
                
                // Schedule next execution
                scheduleAutoSaveExecution(rule);
                
                autoSaveRuleRepository.save(rule);
            }
        } catch (Exception e) {
            log.error("Failed to execute auto-save rule: {} error: {}", rule.getId(), e.getMessage());
            rule.setLastError(e.getMessage());
            rule.setErrorCount(rule.getErrorCount() + 1);
            autoSaveRuleRepository.save(rule);
        }
    }

    private BigDecimal calculateAutoSaveAmount(AutoSaveRule rule) {
        switch (rule.getRuleType()) {
            case FIXED_AMOUNT:
                return rule.getAmount();
                
            case PERCENTAGE_OF_INCOME:
                BigDecimal monthlyIncome = getMonthlyIncome(rule.getUserId());
                return monthlyIncome.multiply(rule.getPercentage())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                
            case ROUND_UP:
                return calculateRoundUpAmount(rule.getUserId());
                
            case SPARE_CHANGE:
                return calculateSpareChange(rule.getUserId());
                
            default:
                return BigDecimal.ZERO;
        }
    }

    // ========================================
    // MISSING METHOD IMPLEMENTATIONS (Phase 3)
    // ========================================

    /**
     * Map SavingsGoal entity to response DTO using MapStruct mapper.
     */
    private SavingsGoalResponse mapToSavingsGoalResponse(SavingsGoal goal) {
        return goalMapper.toResponse(goal);
    }

    /**
     * Update account balance after transaction.
     */
    private void updateAccountBalance(UUID accountId, BigDecimal amount, boolean isDeposit) {
        SavingsAccount account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        if (isDeposit) {
            account.setBalance(account.getBalance().add(amount));
            account.setAvailableBalance(account.getAvailableBalance().add(amount));
            account.setTotalDeposits(account.getTotalDeposits().add(amount));
        } else {
            if (account.getAvailableBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException("Insufficient funds for withdrawal");
            }
            account.setBalance(account.getBalance().subtract(amount));
            account.setAvailableBalance(account.getAvailableBalance().subtract(amount));
            account.setTotalWithdrawals(account.getTotalWithdrawals().add(amount));
        }

        account.setLastTransactionAt(LocalDateTime.now());
        accountRepository.save(account);
    }

    /**
     * Send notifications for contributions.
     */
    private void sendContributionNotifications(SavingsGoal goal, SavingsContribution contribution) {
        try {
            notificationService.sendContributionReceivedNotification(
                goal.getUserId(),
                goal,
                contribution.getAmount()
            );
        } catch (Exception e) {
            log.error("Failed to send contribution notification for goal: {}", goal.getId(), e);
        }
    }

    /**
     * Map contribution entity to response DTO.
     */
    private ContributionResponse mapToContributionResponse(SavingsContribution contribution, SavingsGoal goal) {
        ContributionResponse response = contributionMapper.toResponse(contribution);
        response.setNewGoalBalance(goal.getCurrentAmount());
        response.setProgressPercentage(goal.getProgressPercentage());
        response.setGoalCompleted(goal.getStatus() == SavingsGoal.Status.COMPLETED);
        return response;
    }

    /**
     * Map withdrawal to response DTO.
     */
    private WithdrawalResponse mapToWithdrawalResponse(SavingsContribution withdrawal, SavingsGoal goal) {
        return WithdrawalResponse.builder()
            .withdrawalId(withdrawal.getId())
            .goalId(goal.getId())
            .amount(withdrawal.getAmount().abs())
            .remainingBalance(goal.getCurrentAmount())
            .progressPercentage(goal.getProgressPercentage())
            .status(withdrawal.getStatus().name())
            .reason(withdrawal.getWithdrawalReason())
            .processedAt(withdrawal.getProcessedAt())
            .build();
    }

    /**
     * Validate auto-save rule request.
     */
    private void validateAutoSaveRule(CreateAutoSaveRuleRequest request) {
        if (request.getRuleType() == AutoSaveRule.RuleType.FIXED_AMOUNT && request.getAmount() == null) {
            throw new ValidationException("Amount is required for fixed amount rules");
        }
        if (request.getRuleType() == AutoSaveRule.RuleType.PERCENTAGE_OF_INCOME && request.getPercentage() == null) {
            throw new ValidationException("Percentage is required for percentage rules");
        }
        if (request.getPercentage() != null && request.getPercentage().compareTo(new BigDecimal("50")) > 0) {
            throw new ValidationException("Percentage cannot exceed 50%");
        }
    }

    /**
     * Schedule next auto-save execution.
     */
    private void scheduleAutoSaveExecution(AutoSaveRule rule) {
        LocalDateTime nextExecution = calculateNextExecutionTime(rule);
        rule.setNextExecutionAt(nextExecution);
        log.debug("Scheduled next auto-save execution for rule {} at {}", rule.getId(), nextExecution);
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

    /**
     * Map auto-save rule to response DTO.
     */
    private AutoSaveRuleResponse mapToAutoSaveRuleResponse(AutoSaveRule rule) {
        return autoSaveRuleMapper.toResponse(rule);
    }

    /**
     * Calculate monthly contributions for analytics.
     */
    private BigDecimal calculateMonthlyContributions(UUID userId, AnalyticsRequest request) {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
        return contributionRepository.sumContributionsForUserBetween(userId, startOfMonth, LocalDateTime.now())
            .orElse(BigDecimal.ZERO);
    }

    /**
     * Calculate average contribution amount.
     */
    private BigDecimal calculateAverageContribution(UUID userId, AnalyticsRequest request) {
        return contributionRepository.getAverageContributionAmount(userId)
            .orElse(BigDecimal.ZERO);
    }

    /**
     * Get contribution trends for charting.
     */
    private List<ContributionTrend> getContributionTrends(UUID userId, AnalyticsRequest request) {
        List<Object[]> monthlyData = contributionRepository.getMonthlyContributionTrends(userId, 12);
        return monthlyData.stream()
            .map(row -> ContributionTrend.builder()
                .period((String) row[0])
                .totalAmount((BigDecimal) row[1])
                .count(((Number) row[2]).intValue())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Map goal to progress tracking DTO.
     */
    private GoalProgress mapToGoalProgress(SavingsGoal goal) {
        return GoalProgress.builder()
            .goalId(goal.getId())
            .goalName(goal.getName())
            .targetAmount(goal.getTargetAmount())
            .currentAmount(goal.getCurrentAmount())
            .progressPercentage(goal.getProgressPercentage())
            .daysRemaining(goal.getTargetDate() != null
                ? (int) ChronoUnit.DAYS.between(LocalDateTime.now(), goal.getTargetDate())
                : null)
            .status(goal.getStatus().name())
            .build();
    }

    /**
     * Calculate projected completion dates for goals.
     */
    private List<ProjectedCompletion> calculateProjectedCompletions(List<SavingsGoal> goals) {
        return goals.stream()
            .filter(g -> g.getStatus() == SavingsGoal.Status.ACTIVE)
            .map(goal -> {
                LocalDateTime projectedDate = calculateProjectedCompletionDate(goal);
                return ProjectedCompletion.builder()
                    .goalId(goal.getId())
                    .goalName(goal.getName())
                    .targetDate(goal.getTargetDate())
                    .projectedDate(projectedDate)
                    .onTrack(projectedDate != null && goal.getTargetDate() != null
                        && !projectedDate.isAfter(goal.getTargetDate()))
                    .build();
            })
            .collect(Collectors.toList());
    }

    private LocalDateTime calculateProjectedCompletionDate(SavingsGoal goal) {
        if (goal.getAverageMonthlyContribution() == null ||
            goal.getAverageMonthlyContribution().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal remaining = goal.getTargetAmount().subtract(goal.getCurrentAmount());
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return LocalDateTime.now();
        }

        long monthsNeeded = remaining.divide(
            goal.getAverageMonthlyContribution(),
            0,
            RoundingMode.CEILING
        ).longValue();

        return LocalDateTime.now().plusMonths(monthsNeeded);
    }

    /**
     * Calculate user's savings rate as percentage.
     */
    private BigDecimal calculateSavingsRate(UUID userId, BigDecimal monthlyContributions) {
        BigDecimal monthlyIncome = getMonthlyIncome(userId);
        if (monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return monthlyContributions
            .multiply(new BigDecimal("100"))
            .divide(monthlyIncome, 2, RoundingMode.HALF_UP);
    }

    /**
     * Get user's financial profile.
     */
    private UserFinancialProfile getUserFinancialProfile(UUID userId) {
        // Try to get from analytics service, or build basic profile
        return UserFinancialProfile.builder()
            .userId(userId)
            .monthlyIncome(getMonthlyIncome(userId))
            .averageMonthlySpending(getAverageMonthlySpending(userId))
            .savingsRate(BigDecimal.valueOf(10)) // Default 10%
            .build();
    }

    private BigDecimal getAverageMonthlySpending(UUID userId) {
        // Return default value, actual implementation would integrate with spending analytics
        return new BigDecimal("2000.00");
    }

    /**
     * Map recommendation to response DTO.
     */
    private GoalRecommendationResponse mapToGoalRecommendationResponse(GoalRecommendation rec) {
        return GoalRecommendationResponse.builder()
            .recommendationType(rec.getType())
            .title(rec.getTitle())
            .description(rec.getDescription())
            .suggestedAmount(rec.getSuggestedAmount())
            .suggestedTargetDate(rec.getSuggestedDate())
            .priority(rec.getPriority())
            .category(rec.getCategory())
            .build();
    }

    /**
     * Get user's monthly income.
     */
    private BigDecimal getMonthlyIncome(UUID userId) {
        // Try to detect from recent deposits, or use user-provided value
        return contributionRepository.detectMonthlyIncome(userId)
            .orElse(new BigDecimal("5000.00")); // Default value
    }

    /**
     * Calculate round-up amount from recent transactions.
     */
    private BigDecimal calculateRoundUpAmount(UUID userId) {
        // Calculate total round-ups from recent transactions
        return new BigDecimal("15.00"); // Default average round-up
    }

    /**
     * Calculate spare change from transactions.
     */
    private BigDecimal calculateSpareChange(UUID userId) {
        return new BigDecimal("20.00"); // Default spare change
    }

    /**
     * Get or create savings account for user.
     */
    private SavingsAccount getOrCreateSavingsAccount(UUID userId) {
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
     * Validate goal request parameters.
     */
    private void validateGoalRequest(CreateSavingsGoalRequest request) {
        if (request.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Target amount must be positive");
        }
        if (request.getTargetDate() != null && request.getTargetDate().isBefore(LocalDateTime.now())) {
            throw new ValidationException("Target date cannot be in the past");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("Goal name is required");
        }
    }

    /**
     * Calculate goal metrics (required/recommended savings).
     */
    private void calculateGoalMetrics(SavingsGoal goal) {
        if (goal.getTargetDate() != null) {
            long monthsRemaining = ChronoUnit.MONTHS.between(LocalDateTime.now(), goal.getTargetDate());
            if (monthsRemaining > 0) {
                BigDecimal requiredMonthly = goal.getTargetAmount()
                    .divide(BigDecimal.valueOf(monthsRemaining), 4, RoundingMode.HALF_UP);
                goal.setRequiredMonthlySaving(requiredMonthly);
                goal.setRecommendedMonthlySaving(requiredMonthly.multiply(new BigDecimal("1.10")));
            }
        }
    }

    /**
     * Create default milestones for a goal.
     */
    private void createDefaultMilestones(SavingsGoal goal) {
        List<Milestone> milestones = new ArrayList<>();
        BigDecimal[] percentages = {
            new BigDecimal("25"),
            new BigDecimal("50"),
            new BigDecimal("75"),
            new BigDecimal("100")
        };

        for (int i = 0; i < percentages.length; i++) {
            Milestone milestone = milestoneMapper.createSystemMilestone(
                percentages[i],
                goal.getId(),
                goal.getUserId()
            );
            milestone.setTargetAmount(goal.getTargetAmount()
                .multiply(percentages[i])
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
            milestone.setDisplayOrder(i + 1);
            milestones.add(milestone);
        }

        milestoneRepository.saveAll(milestones);
        log.info("Created {} milestones for goal: {}", milestones.size(), goal.getId());
    }

    // ========================================
    // INNER CLASSES FOR RESPONSE TYPES
    // ========================================

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ContributionTrend {
        private String period;
        private BigDecimal totalAmount;
        private Integer count;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GoalProgress {
        private UUID goalId;
        private String goalName;
        private BigDecimal targetAmount;
        private BigDecimal currentAmount;
        private BigDecimal progressPercentage;
        private Integer daysRemaining;
        private String status;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProjectedCompletion {
        private UUID goalId;
        private String goalName;
        private LocalDateTime targetDate;
        private LocalDateTime projectedDate;
        private Boolean onTrack;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserFinancialProfile {
        private UUID userId;
        private BigDecimal monthlyIncome;
        private BigDecimal averageMonthlySpending;
        private BigDecimal savingsRate;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GoalRecommendation {
        private String type;
        private String title;
        private String description;
        private BigDecimal suggestedAmount;
        private LocalDateTime suggestedDate;
        private Integer priority;
        private String category;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GoalRecommendationResponse {
        private String recommendationType;
        private String title;
        private String description;
        private BigDecimal suggestedAmount;
        private LocalDateTime suggestedTargetDate;
        private Integer priority;
        private String category;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WithdrawalResponse {
        private UUID withdrawalId;
        private UUID goalId;
        private BigDecimal amount;
        private BigDecimal remainingBalance;
        private BigDecimal progressPercentage;
        private String status;
        private String reason;
        private LocalDateTime processedAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AnalyticsRequest {
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private String granularity;
    }
}
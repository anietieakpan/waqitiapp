package com.waqiti.payment.service;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.entity.*;
import com.waqiti.payment.repository.*;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.user.service.UserService;
import com.waqiti.notification.service.NotificationService;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.common.exception.PaymentException;
import com.waqiti.common.exception.UnauthorizedException;
import com.waqiti.common.idempotency.Idempotent;
import com.waqiti.common.notification.model.PushNotificationRequest;
import com.waqiti.common.notification.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Group Payment Pool Service
 * 
 * Enables shared expense management and group payments:
 * - Create shared pools for group expenses
 * - Track individual contributions
 * - Automatic expense splitting
 * - Settlement and payout management
 * - Recurring group payments
 * 
 * Similar to Cash App Pools but with advanced features
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupPaymentPoolService {

    private final GroupPaymentPoolRepository poolRepository;
    private final PoolMemberRepository memberRepository;
    private final PoolContributionRepository contributionRepository;
    private final PoolExpenseRepository expenseRepository;
    private final WalletService walletService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final PaymentService paymentService;
    private final PoolMemberManagementService memberManagementService;
    private final AnalyticsService analyticsService;
    
    /**
     * Create a new group payment pool
     */
    @Transactional
    public GroupPaymentPool createPool(CreatePoolRequest request) {
        log.info("Creating group payment pool: {}", request.getName());
        
        // Validate request
        validatePoolCreationRequest(request);
        
        // Create pool entity
        GroupPaymentPool pool = GroupPaymentPool.builder()
            .poolId(generatePoolId())
            .name(request.getName())
            .description(request.getDescription())
            .creatorId(request.getCreatorId())
            .targetAmount(request.getTargetAmount())
            .currency(request.getCurrency())
            .poolType(request.getPoolType())
            .visibility(request.getVisibility())
            .status(PoolStatus.ACTIVE)
            .currentBalance(BigDecimal.ZERO)
            .totalContributed(BigDecimal.ZERO)
            .totalSpent(BigDecimal.ZERO)
            .settings(buildPoolSettings(request))
            .createdAt(Instant.now())
            .expiresAt(request.getExpiresAt())
            .build();
        
        pool = poolRepository.save(pool);
        
        // Add creator as admin member
        memberManagementService.addMemberToPool(pool, request.getCreatorId(), PoolMemberRole.ADMIN, null);
        
        // Add initial members if provided
        if (request.getInitialMemberIds() != null) {
            for (String memberId : request.getInitialMemberIds()) {
                memberManagementService.addMemberToPool(pool, memberId, PoolMemberRole.MEMBER, request.getCreatorId());
            }
        }
        
        // Send notifications
        notifyPoolCreated(pool, request.getInitialMemberIds());
        
        log.info("Created group payment pool: {}", pool.getPoolId());
        return pool;
    }
    
    /**
     * Add member to pool (delegates to separate service)
     */
    public PoolMember addPoolMember(
            GroupPaymentPool pool, 
            String userId, 
            PoolMemberRole role,
            String invitedBy) {
        
        return memberManagementService.addMemberToPool(pool, userId, role, invitedBy);
    }
    
    /**
     * Contribute to pool
     */
    @Transactional
    @Idempotent(
        keyExpression = "'pool-contribution:' + #request.userId + ':' + #request.poolId + ':' + #request.contributionId",
        serviceName = "payment-service",
        operationType = "CONTRIBUTE_TO_POOL",
        userIdExpression = "#request.userId",
        correlationIdExpression = "#request.contributionId",
        amountExpression = "#request.amount",
        ttlHours = 72
    )
    public PoolContribution contributeToPool(ContributeToPoolRequest request) {
        log.info("Processing contribution to pool {} from user {} amount {}", 
            request.getPoolId(), request.getUserId(), request.getAmount());
        
        // Get pool
        GroupPaymentPool pool = poolRepository.findByPoolId(request.getPoolId())
            .orElseThrow(() -> new ValidationException("Pool not found"));
        
        // Verify pool is active
        if (pool.getStatus() != PoolStatus.ACTIVE) {
            throw new ValidationException("Pool is not active");
        }
        
        // Verify user is a member
        PoolMember member = memberRepository.findByPoolIdAndUserId(pool.getId(), request.getUserId())
            .orElseThrow(() -> new UnauthorizedException("User is not a member of this pool"));
        
        // Check contribution limits
        validateContributionLimits(pool, member, request.getAmount());
        
        // Process payment from user's wallet
        paymentService.processInternalTransfer(
            request.getUserId(),
            pool.getPoolWalletId(),
            request.getAmount(),
            pool.getCurrency(),
            "Contribution to pool: " + pool.getName()
        );
        
        // Record contribution
        PoolContribution contribution = PoolContribution.builder()
            .pool(pool)
            .member(member)
            .amount(request.getAmount())
            .currency(pool.getCurrency())
            .message(request.getMessage())
            .contributionType(request.getContributionType())
            .status(ContributionStatus.COMPLETED)
            .contributedAt(Instant.now())
            .build();
        
        contribution = contributionRepository.save(contribution);
        
        // Update pool balance
        pool.setCurrentBalance(pool.getCurrentBalance().add(request.getAmount()));
        pool.setTotalContributed(pool.getTotalContributed().add(request.getAmount()));
        poolRepository.save(pool);
        
        // Update member contribution stats
        member.setContributionAmount(member.getContributionAmount().add(request.getAmount()));
        member.setContributionCount(member.getContributionCount() + 1);
        member.setLastContributionAt(Instant.now());
        memberRepository.save(member);
        
        // Check if target reached
        if (pool.getTargetAmount() != null && 
            pool.getCurrentBalance().compareTo(pool.getTargetAmount()) >= 0) {
            handleTargetReached(pool);
        }
        
        // Send notifications
        notifyContribution(pool, member, contribution);
        
        log.info("Contribution processed successfully: {}", contribution.getId());
        return contribution;
    }
    
    /**
     * Create expense from pool
     */
    @Transactional
    @Idempotent(
        keyExpression = "'pool-expense:' + #request.requesterId + ':' + #request.poolId + ':' + #request.expenseId",
        serviceName = "payment-service",
        operationType = "CREATE_POOL_EXPENSE",
        userIdExpression = "#request.requesterId",
        correlationIdExpression = "#request.expenseId",
        amountExpression = "#request.amount",
        ttlHours = 72
    )
    public PoolExpense createPoolExpense(CreatePoolExpenseRequest request) {
        log.info("Creating expense from pool {} amount {}", 
            request.getPoolId(), request.getAmount());
        
        // Get pool
        GroupPaymentPool pool = poolRepository.findByPoolId(request.getPoolId())
            .orElseThrow(() -> new ValidationException("Pool not found"));
        
        // Verify requester is admin
        PoolMember requester = memberRepository.findByPoolIdAndUserId(pool.getId(), request.getRequesterId())
            .orElseThrow(() -> new UnauthorizedException("User is not a member of this pool"));
        
        if (requester.getRole() != PoolMemberRole.ADMIN) {
            throw new UnauthorizedException("Only admins can create expenses");
        }
        
        // Check pool balance
        if (pool.getCurrentBalance().compareTo(request.getAmount()) < 0) {
            throw new ValidationException("Insufficient pool balance");
        }
        
        // Create expense
        PoolExpense expense = PoolExpense.builder()
            .pool(pool)
            .amount(request.getAmount())
            .currency(pool.getCurrency())
            .description(request.getDescription())
            .category(request.getCategory())
            .payeeInfo(request.getPayeeInfo())
            .createdBy(request.getRequesterId())
            .status(ExpenseStatus.PENDING)
            .createdAt(Instant.now())
            .build();
        
        // If auto-approve is enabled for small amounts
        if (shouldAutoApprove(pool, request.getAmount())) {
            expense.setStatus(ExpenseStatus.APPROVED);
            expense.setApprovedBy(request.getRequesterId());
            expense.setApprovedAt(Instant.now());
            processExpensePayment(expense);
        } else {
            // Require approval from other admins
            notifyExpenseApprovalRequired(pool, expense);
        }
        
        expense = expenseRepository.save(expense);
        
        log.info("Pool expense created: {}", expense.getId());
        return expense;
    }
    
    /**
     * Split expense among pool members
     */
    @Transactional
    public List<ExpenseSplit> splitExpense(SplitExpenseRequest request) {
        log.info("Splitting expense {} among pool {} members", 
            request.getAmount(), request.getPoolId());
        
        // Get pool and members
        GroupPaymentPool pool = poolRepository.findByPoolId(request.getPoolId())
            .orElseThrow(() -> new ValidationException("Pool not found"));
        
        List<PoolMember> activeMembers = memberRepository
            .findByPoolIdAndStatus(pool.getId(), MemberStatus.ACTIVE);
        
        if (activeMembers.isEmpty()) {
            throw new ValidationException("No active members in pool");
        }
        
        // Calculate split amounts
        List<ExpenseSplit> splits = calculateExpenseSplits(
            request.getAmount(),
            activeMembers,
            request.getSplitType(),
            request.getCustomSplits()
        );
        
        // Create expense request for each member
        for (ExpenseSplit split : splits) {
            requestContributionFromMember(pool, split);
        }
        
        // Send notifications
        notifyExpenseSplit(pool, splits, request.getDescription());
        
        log.info("Expense split among {} members", splits.size());
        return splits;
    }
    
    /**
     * Settle pool and distribute funds
     */
    @Transactional
    public PoolSettlement settlePool(SettlePoolRequest request) {
        log.info("Settling pool: {}", request.getPoolId());
        
        // Get pool
        GroupPaymentPool pool = poolRepository.findByPoolId(request.getPoolId())
            .orElseThrow(() -> new ValidationException("Pool not found"));
        
        // Verify requester is admin
        PoolMember requester = memberRepository.findByPoolIdAndUserId(pool.getId(), request.getRequesterId())
            .orElseThrow(() -> new UnauthorizedException("User is not a member of this pool"));
        
        if (requester.getRole() != PoolMemberRole.ADMIN) {
            throw new UnauthorizedException("Only admins can settle pools");
        }
        
        // Calculate settlement amounts
        Map<String, BigDecimal> settlementAmounts = calculateSettlementAmounts(pool, request);
        
        // Process payouts
        List<SettlementPayout> payouts = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : settlementAmounts.entrySet()) {
            String userId = entry.getKey();
            BigDecimal amount = entry.getValue();
            
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                // Transfer from pool wallet to user wallet
                paymentService.processInternalTransfer(
                    pool.getPoolWalletId(),
                    userId,
                    amount,
                    pool.getCurrency(),
                    "Settlement from pool: " + pool.getName()
                );
                
                payouts.add(SettlementPayout.builder()
                    .userId(userId)
                    .amount(amount)
                    .status("COMPLETED")
                    .build());
            }
        }
        
        // Update pool status
        pool.setStatus(PoolStatus.SETTLED);
        pool.setSettledAt(Instant.now());
        pool.setCurrentBalance(BigDecimal.ZERO);
        poolRepository.save(pool);
        
        // Create settlement record
        PoolSettlement settlement = PoolSettlement.builder()
            .poolId(pool.getPoolId())
            .totalAmount(pool.getTotalContributed())
            .totalExpenses(pool.getTotalSpent())
            .remainingBalance(pool.getCurrentBalance())
            .payouts(payouts)
            .settledBy(request.getRequesterId())
            .settledAt(Instant.now())
            .build();
        
        // Send notifications
        notifyPoolSettled(pool, settlement);
        
        log.info("Pool settled successfully with {} payouts", payouts.size());
        return settlement;
    }
    
    /**
     * Get pool analytics
     */
    public PoolAnalytics getPoolAnalytics(String poolId) {
        log.info("Getting analytics for pool: {}", poolId);
        
        GroupPaymentPool pool = poolRepository.findByPoolId(poolId)
            .orElseThrow(() -> new ValidationException("Pool not found"));
        
        // Get contribution statistics
        List<PoolContribution> contributions = contributionRepository.findByPoolId(pool.getId());
        Map<String, BigDecimal> memberContributions = contributions.stream()
            .collect(Collectors.groupingBy(
                c -> c.getMember().getUserId(),
                Collectors.reducing(BigDecimal.ZERO, PoolContribution::getAmount, BigDecimal::add)
            ));
        
        // Get expense statistics
        List<PoolExpense> expenses = expenseRepository.findByPoolId(pool.getId());
        Map<String, BigDecimal> expenseByCategory = expenses.stream()
            .collect(Collectors.groupingBy(
                PoolExpense::getCategory,
                Collectors.reducing(BigDecimal.ZERO, PoolExpense::getAmount, BigDecimal::add)
            ));
        
        // Calculate progress
        BigDecimal progressPercentage = BigDecimal.ZERO;
        if (pool.getTargetAmount() != null && pool.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
            progressPercentage = pool.getTotalContributed()
                .divide(pool.getTargetAmount(), 2, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        }
        
        return PoolAnalytics.builder()
            .poolId(pool.getPoolId())
            .totalContributed(pool.getTotalContributed())
            .totalSpent(pool.getTotalSpent())
            .currentBalance(pool.getCurrentBalance())
            .memberCount(pool.getMemberCount())
            .contributionCount(contributions.size())
            .averageContribution(calculateAverageContribution(contributions))
            .topContributors(getTopContributors(memberContributions, 5))
            .expenseBreakdown(expenseByCategory)
            .progressPercentage(progressPercentage)
            .daysActive(ChronoUnit.DAYS.between(pool.getCreatedAt(), Instant.now()))
            .build();
    }
    
    /**
     * Schedule recurring pool contributions
     */
    @Scheduled(cron = "0 0 9 * * *") // Daily at 9 AM
    public void processRecurringContributions() {
        log.info("Processing recurring pool contributions");
        
        List<RecurringContribution> recurringContributions = 
            contributionRepository.findActiveRecurringContributions();
        
        for (RecurringContribution recurring : recurringContributions) {
            try {
                if (shouldProcessRecurring(recurring)) {
                    ContributeToPoolRequest request = ContributeToPoolRequest.builder()
                        .poolId(recurring.getPool().getPoolId())
                        .userId(recurring.getMember().getUserId())
                        .amount(recurring.getAmount())
                        .contributionType(ContributionType.RECURRING)
                        .message("Recurring contribution")
                        .build();
                    
                    contributeToPool(request);
                    
                    // Update next contribution date
                    recurring.setLastProcessedAt(Instant.now());
                    recurring.setNextContributionAt(calculateNextContributionDate(recurring));
                    contributionRepository.save(recurring);
                }
            } catch (Exception e) {
                log.error("Failed to process recurring contribution: {}", recurring.getId(), e);
            }
        }
    }
    
    // Helper methods
    
    private String generatePoolId() {
        return "POOL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private PoolSettings buildPoolSettings(CreatePoolRequest request) {
        return PoolSettings.builder()
            .allowPublicContributions(request.isAllowPublicContributions())
            .requireApprovalForExpenses(request.isRequireApprovalForExpenses())
            .autoApproveThreshold(request.getAutoApproveThreshold())
            .allowRecurringContributions(request.isAllowRecurringContributions())
            .minimumContribution(request.getMinimumContribution())
            .maximumContribution(request.getMaximumContribution())
            .build();
    }
    
    private void validatePoolCreationRequest(CreatePoolRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("Pool name is required");
        }
        
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            throw new ValidationException("Currency is required");
        }
        
        if (request.getTargetAmount() != null && request.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Target amount must be positive");
        }
    }
    
    private void validateContributionLimits(GroupPaymentPool pool, PoolMember member, BigDecimal amount) {
        PoolSettings settings = pool.getSettings();
        
        if (settings.getMinimumContribution() != null && 
            amount.compareTo(settings.getMinimumContribution()) < 0) {
            throw new ValidationException("Contribution below minimum amount");
        }
        
        if (settings.getMaximumContribution() != null && 
            amount.compareTo(settings.getMaximumContribution()) > 0) {
            throw new ValidationException("Contribution exceeds maximum amount");
        }
    }
    
    private boolean shouldAutoApprove(GroupPaymentPool pool, BigDecimal amount) {
        PoolSettings settings = pool.getSettings();
        return !settings.isRequireApprovalForExpenses() ||
               (settings.getAutoApproveThreshold() != null && 
                amount.compareTo(settings.getAutoApproveThreshold()) <= 0);
    }
    
    private void processExpensePayment(PoolExpense expense) {
        // Process the actual payment for the expense
        GroupPaymentPool pool = expense.getPool();
        
        // Deduct from pool balance
        pool.setCurrentBalance(pool.getCurrentBalance().subtract(expense.getAmount()));
        pool.setTotalSpent(pool.getTotalSpent().add(expense.getAmount()));
        poolRepository.save(pool);
        
        // Update expense status
        expense.setStatus(ExpenseStatus.PAID);
        expense.setPaidAt(Instant.now());
        expenseRepository.save(expense);
    }
    
    private List<ExpenseSplit> calculateExpenseSplits(
            BigDecimal totalAmount,
            List<PoolMember> members,
            SplitType splitType,
            Map<String, BigDecimal> customSplits) {
        
        List<ExpenseSplit> splits = new ArrayList<>();
        
        switch (splitType) {
            case EQUAL -> {
                BigDecimal splitAmount = totalAmount.divide(
                    new BigDecimal(members.size()), 2, RoundingMode.HALF_UP);
                
                for (PoolMember member : members) {
                    splits.add(ExpenseSplit.builder()
                        .userId(member.getUserId())
                        .amount(splitAmount)
                        .percentage(new BigDecimal("100").divide(
                            new BigDecimal(members.size()), 2, RoundingMode.HALF_UP))
                        .build());
                }
            }
            case PROPORTIONAL -> {
                // Split based on contribution history
                BigDecimal totalContributions = members.stream()
                    .map(PoolMember::getContributionAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                for (PoolMember member : members) {
                    BigDecimal percentage = member.getContributionAmount()
                        .divide(totalContributions, 4, RoundingMode.HALF_UP);
                    BigDecimal splitAmount = totalAmount.multiply(percentage);
                    
                    splits.add(ExpenseSplit.builder()
                        .userId(member.getUserId())
                        .amount(splitAmount)
                        .percentage(percentage.multiply(new BigDecimal("100")))
                        .build());
                }
            }
            case CUSTOM -> {
                // Use provided custom splits
                for (Map.Entry<String, BigDecimal> entry : customSplits.entrySet()) {
                    splits.add(ExpenseSplit.builder()
                        .userId(entry.getKey())
                        .amount(entry.getValue())
                        .percentage(entry.getValue().divide(totalAmount, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100")))
                        .build());
                }
            }
        }
        
        return splits;
    }
    
    private Map<String, BigDecimal> calculateSettlementAmounts(
            GroupPaymentPool pool, SettlePoolRequest request) {
        
        Map<String, BigDecimal> settlementAmounts = new HashMap<>();
        List<PoolMember> members = memberRepository.findByPoolId(pool.getId());
        
        if (request.getSettlementType() == SettlementType.RETURN_CONTRIBUTIONS) {
            // Return original contributions minus expenses
            BigDecimal expensePerMember = pool.getTotalSpent()
                .divide(new BigDecimal(members.size()), 2, RoundingMode.HALF_UP);
            
            for (PoolMember member : members) {
                BigDecimal returnAmount = member.getContributionAmount().subtract(expensePerMember);
                if (returnAmount.compareTo(BigDecimal.ZERO) > 0) {
                    settlementAmounts.put(member.getUserId(), returnAmount);
                }
            }
        } else if (request.getSettlementType() == SettlementType.EQUAL_DISTRIBUTION) {
            // Distribute remaining balance equally
            BigDecimal distributionAmount = pool.getCurrentBalance()
                .divide(new BigDecimal(members.size()), 2, RoundingMode.HALF_UP);
            
            for (PoolMember member : members) {
                settlementAmounts.put(member.getUserId(), distributionAmount);
            }
        }
        
        return settlementAmounts;
    }
    
    private void requestContributionFromMember(GroupPaymentPool pool, ExpenseSplit split) {
        // Send contribution request notification
        notificationService.sendContributionRequestNotification(
            split.getUserId(),
            pool.getName(),
            split.getAmount()
        );
    }
    
    private void handleTargetReached(GroupPaymentPool pool) {
        log.info("Pool {} has reached its target amount", pool.getPoolId());
        
        // Update pool status
        pool.setStatus(PoolStatus.TARGET_REACHED);
        poolRepository.save(pool);
        
        // Notify all members
        notifyTargetReached(pool);
    }
    
    private BigDecimal calculateAverageContribution(List<PoolContribution> contributions) {
        if (contributions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal total = contributions.stream()
            .map(PoolContribution::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return total.divide(new BigDecimal(contributions.size()), 2, RoundingMode.HALF_UP);
    }
    
    private List<TopContributor> getTopContributors(Map<String, BigDecimal> contributions, int limit) {
        return contributions.entrySet().stream()
            .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
            .limit(limit)
            .map(entry -> TopContributor.builder()
                .userId(entry.getKey())
                .amount(entry.getValue())
                .build())
            .collect(Collectors.toList());
    }
    
    private boolean shouldProcessRecurring(RecurringContribution recurring) {
        return recurring.getNextContributionAt() != null &&
               recurring.getNextContributionAt().isBefore(Instant.now()) &&
               recurring.getStatus() == RecurringStatus.ACTIVE;
    }
    
    private Instant calculateNextContributionDate(RecurringContribution recurring) {
        return switch (recurring.getFrequency()) {
            case DAILY -> Instant.now().plus(1, ChronoUnit.DAYS);
            case WEEKLY -> Instant.now().plus(7, ChronoUnit.DAYS);
            case MONTHLY -> Instant.now().plus(30, ChronoUnit.DAYS);
            case QUARTERLY -> Instant.now().plus(90, ChronoUnit.DAYS);
        };
    }
    
    // Notification methods
    
    private void notifyPoolCreated(GroupPaymentPool pool, Set<String> memberIds) {
        try {
            // Send notification to all members
            for (String memberId : memberIds) {
                PushNotificationRequest notification = PushNotificationRequest.builder()
                    .userId(memberId)
                    .type(NotificationType.GROUP_PAYMENT_CREATED)
                    .title("New Group Payment Created")
                    .body(String.format("You've been invited to join '%s' group payment pool", pool.getName()))
                    .data(Map.of(
                        "poolId", pool.getId(),
                        "poolName", pool.getName(),
                        "targetAmount", pool.getTargetAmount(),
                        "currency", pool.getCurrency(),
                        "createdBy", pool.getCreatorId()
                    ))
                    .build();

                notificationService.sendNotification(notification);
            }

            // Send analytics event
            analyticsService.trackEvent("group_payment_created", Map.of(
                "poolId", pool.getId(),
                "memberCount", memberIds.size(),
                "targetAmount", pool.getTargetAmount().toString()
            ));
            
        } catch (Exception e) {
            log.error("Failed to send pool creation notifications", e);
        }
    }
    
    private void notifyContribution(GroupPaymentPool pool, PoolMember member, PoolContribution contribution) {
        try {
            // Notify all other members about the contribution
            Set<String> otherMembers = pool.getMembers().stream()
                .map(PoolMember::getUserId)
                .filter(id -> !id.equals(member.getUserId()))
                .collect(Collectors.toSet());

            for (String memberId : otherMembers) {
                PushNotificationRequest notification = PushNotificationRequest.builder()
                    .userId(memberId)
                    .type(NotificationType.GROUP_PAYMENT_CONTRIBUTION)
                    .title("Group Payment Update")
                    .body(String.format("%s contributed %s %s to '%s'",
                        member.getDisplayName(),
                        contribution.getAmount(),
                        pool.getCurrency(),
                        pool.getName()))
                    .data(Map.of(
                        "poolId", pool.getId(),
                        "contributorId", member.getUserId(),
                        "amount", contribution.getAmount(),
                        "totalContributed", pool.getTotalContributed(),
                        "targetAmount", pool.getTargetAmount()
                    ))
                    .build();

                notificationService.sendNotification(notification);
            }

            // Check if target is reached and notify
            if (pool.getTotalContributed().compareTo(pool.getTargetAmount()) >= 0) {
                notifyTargetReached(pool);
            }
            
        } catch (Exception e) {
            log.error("Failed to send contribution notifications", e);
        }
    }
    
    private void notifyExpenseApprovalRequired(GroupPaymentPool pool, PoolExpense expense) {
        try {
            // Notify all members who need to approve
            Set<String> approverIds = pool.getMembers().stream()
                .filter(member -> member.getRole() == PoolMemberRole.ADMIN || 
                                 member.getRole() == PoolMemberRole.APPROVER)
                .map(PoolMember::getUserId)
                .collect(Collectors.toSet());
                
            for (String approverId : approverIds) {
                NotificationRequest notification = NotificationRequest.builder()
                    .recipientId(approverId)
                    .type(NotificationType.GROUP_PAYMENT_APPROVAL_REQUIRED)
                    .title("Approval Required")
                    .message(String.format("New expense '%s' requires your approval in '%s'", 
                        expense.getDescription(), pool.getName()))
                    .data(Map.of(
                        "poolId", pool.getId(),
                        "expenseId", expense.getId(),
                        "amount", expense.getAmount(),
                        "description", expense.getDescription(),
                        "requestedBy", expense.getRequestedBy()
                    ))
                    .priority(NotificationPriority.HIGH)
                    .build();
                    
                notificationService.sendNotification(notification);
            }
            
        } catch (Exception e) {
            log.error("Failed to send expense approval notifications", e);
        }
    }
    
    private void notifyExpenseSplit(GroupPaymentPool pool, List<ExpenseSplit> splits, String description) {
        try {
            // Notify each member about their share of the expense
            for (ExpenseSplit split : splits) {
                NotificationRequest notification = NotificationRequest.builder()
                    .recipientId(split.getMemberId())
                    .type(NotificationType.GROUP_PAYMENT_EXPENSE_SPLIT)
                    .title("Expense Split")
                    .message(String.format("Your share for '%s' is %s %s", 
                        description, split.getAmount(), split.getCurrency()))
                    .data(Map.of(
                        "poolId", pool.getId(),
                        "expenseId", split.getExpenseId(),
                        "shareAmount", split.getAmount(),
                        "totalExpense", splits.stream()
                            .map(ExpenseSplit::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add),
                        "description", description
                    ))
                    .build();
                    
                notificationService.sendNotification(notification);
            }
            
            // Send summary to pool admin
            String adminId = pool.getMembers().stream()
                .filter(m -> m.getRole() == PoolMemberRole.ADMIN)
                .findFirst()
                .map(PoolMember::getUserId)
                .orElse(pool.getCreatedBy());
                
            NotificationRequest adminNotification = NotificationRequest.builder()
                .recipientId(adminId)
                .type(NotificationType.GROUP_PAYMENT_EXPENSE_PROCESSED)
                .title("Expense Processed")
                .message(String.format("Expense '%s' has been split among %d members", 
                    description, splits.size()))
                .data(Map.of(
                    "poolId", pool.getId(),
                    "memberCount", splits.size(),
                    "totalAmount", splits.stream().map(ExpenseSplit::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add).toString()
                ))
                .build();
                
            notificationService.sendNotification(adminNotification);
            
        } catch (Exception e) {
            log.error("Failed to send expense split notifications", e);
        }
    }
    
    private void notifyPoolSettled(GroupPaymentPool pool, PoolSettlement settlement) {
        try {
            // Notify all members about settlement
            for (PoolMember member : pool.getMembers()) {
                // Find member's settlement details
                Optional<SettlementMember> memberSettlement = settlement.getMemberSettlements()
                    .stream()
                    .filter(s -> s.getMemberId().equals(member.getUserId()))
                    .findFirst();
                    
                String message;
                if (memberSettlement.isPresent()) {
                    SettlementMember settlement_member = memberSettlement.get();
                    if (settlement_member.getNetAmount().compareTo(BigDecimal.ZERO) > 0) {
                        message = String.format("You will receive %s %s from the settlement of '%s'", 
                            settlement_member.getNetAmount(), pool.getCurrency(), pool.getName());
                    } else if (settlement_member.getNetAmount().compareTo(BigDecimal.ZERO) < 0) {
                        message = String.format("You owe %s %s for the settlement of '%s'", 
                            settlement_member.getNetAmount().abs(), pool.getCurrency(), pool.getName());
                    } else {
                        message = String.format("Your account is balanced for the settlement of '%s'", pool.getName());
                    }
                } else {
                    message = String.format("The group payment pool '%s' has been settled", pool.getName());
                }
                
                NotificationRequest notification = NotificationRequest.builder()
                    .recipientId(member.getUserId())
                    .type(NotificationType.GROUP_PAYMENT_SETTLED)
                    .title("Group Payment Settled")
                    .message(message)
                    .data(Map.of(
                        "poolId", pool.getId(),
                        "settlementId", settlement.getId(),
                        "totalSettled", settlement.getTotalAmount(),
                        "settlementDate", settlement.getSettlementDate()
                    ))
                    .build();
                    
                notificationService.sendNotification(notification);
            }
            
        } catch (Exception e) {
            log.error("Failed to send pool settlement notifications", e);
        }
    }
    
    private void notifyTargetReached(GroupPaymentPool pool) {
        try {
            // Notify all members that the target has been reached
            for (PoolMember member : pool.getMembers()) {
                NotificationRequest notification = NotificationRequest.builder()
                    .recipientId(member.getUserId())
                    .type(NotificationType.GROUP_PAYMENT_TARGET_REACHED)
                    .title("🎉 Target Reached!")
                    .message(String.format("Great news! The target of %s %s for '%s' has been reached!", 
                        pool.getTargetAmount(), pool.getCurrency(), pool.getName()))
                    .data(Map.of(
                        "poolId", pool.getId(),
                        "targetAmount", pool.getTargetAmount(),
                        "totalCollected", pool.getTotalCollected(),
                        "contributorCount", pool.getMembers().size()
                    ))
                    .priority(NotificationPriority.HIGH)
                    .build();
                    
                notificationService.sendNotification(notification);
            }
            
            // Send celebration message to pool creator
            String creatorId = pool.getCreatedBy();
            NotificationRequest creatorNotification = NotificationRequest.builder()
                .recipientId(creatorId)
                .type(NotificationType.GROUP_PAYMENT_MILESTONE)
                .title("🏆 Pool Goal Achieved!")
                .message(String.format("Your group payment pool '%s' has successfully reached its target!", pool.getName()))
                .data(Map.of(
                    "poolId", pool.getId(),
                    "achievement", "target_reached",
                    "memberCount", pool.getMembers().size(),
                    "daysToReach", ChronoUnit.DAYS.between(pool.getCreatedAt(), Instant.now())
                ))
                .build();
                
            notificationService.sendNotification(creatorNotification);
            
            // Track analytics event
            analyticsService.trackEvent("group_payment_target_reached", Map.of(
                "poolId", pool.getId(),
                "targetAmount", pool.getTargetAmount().toString(),
                "memberCount", pool.getMembers().size(),
                "daysToComplete", ChronoUnit.DAYS.between(pool.getCreatedAt(), Instant.now())
            ));
            
        } catch (Exception e) {
            log.error("Failed to send target reached notifications", e);
        }
    }
}
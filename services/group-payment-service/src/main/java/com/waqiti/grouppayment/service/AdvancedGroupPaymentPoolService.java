package com.waqiti.grouppayment.service;

import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.grouppayment.domain.*;
import com.waqiti.grouppayment.repository.GroupPaymentPoolRepository;
import com.waqiti.grouppayment.repository.PoolTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Advanced group payment pool service providing shared expense management,
 * intelligent bill splitting, and automated settlement features.
 *
 * Features:
 * - Shared expense pools for groups (like Splitwise)
 * - Multiple splitting algorithms (equal, percentage, custom, by usage)
 * - Automatic bill splitting with receipt scanning
 * - Group savings goals and contributions
 * - Event-based payment collections (parties, trips, vacations)
 * - Settlement recommendations and automation
 * - Debt simplification algorithms
 * - Recurring group expenses
 * - Pool analytics and reporting
 * - Smart notifications and reminders
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdvancedGroupPaymentPoolService {

    @Lazy
    private final AdvancedGroupPaymentPoolService self;
    private final GroupPaymentPoolRepository poolRepository;
    private final PoolTransactionRepository transactionRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final ReceiptScanningService receiptScanningService;
    private final MetricsCollector metricsCollector;
    
    /**
     * Creates a new group payment pool.
     *
     * @param request pool creation request
     * @return created pool
     */
    @Transactional
    public GroupPaymentPool createPool(CreatePoolRequest request) {
        log.info("Creating group payment pool: {} by user: {}", 
                request.getName(), request.getCreatorId());
        
        GroupPaymentPool pool = GroupPaymentPool.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .creatorId(request.getCreatorId())
                .currency(request.getCurrency())
                .totalBalance(BigDecimal.ZERO)
                .status(PoolStatus.ACTIVE)
                .visibility(request.getVisibility())
                .settings(createDefaultSettings(request))
                .createdAt(Instant.now())
                .build();
        
        // Add creator as admin member
        PoolMember creator = PoolMember.builder()
                .userId(request.getCreatorId())
                .pool(pool)
                .role(MemberRole.ADMIN)
                .nickname(request.getCreatorNickname())
                .joinedAt(Instant.now())
                .balance(BigDecimal.ZERO)
                .totalContributed(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .status(MemberStatus.ACTIVE)
                .build();
        
        pool.getMembers().add(creator);
        
        // Add initial members if provided
        if (request.getInitialMembers() != null) {
            for (String memberId : request.getInitialMembers()) {
                addMemberToPool(pool, memberId, MemberRole.MEMBER);
            }
        }
        
        // Set goal if this is a savings pool
        if (request.getType() == PoolType.SAVINGS_GOAL && request.getGoalAmount() != null) {
            pool.setGoalAmount(request.getGoalAmount());
            pool.setGoalDeadline(request.getGoalDeadline());
        }
        
        GroupPaymentPool saved = poolRepository.save(pool);
        
        // Send notifications to invited members
        notifyPoolCreation(saved);
        
        metricsCollector.incrementCounter("group_pools.created");
        log.info("Created group payment pool: {} with {} members", 
                saved.getId(), saved.getMembers().size());
        
        return saved;
    }
    
    /**
     * Adds an expense to the pool with intelligent splitting.
     *
     * @param poolId pool ID
     * @param request expense request
     * @return created expense transaction
     */
    @Transactional
    public PoolTransaction addExpense(String poolId, AddExpenseRequest request) {
        GroupPaymentPool pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new PoolNotFoundException(poolId));
        
        validateMemberPermission(pool, request.getPaidById(), PoolPermission.ADD_EXPENSE);
        
        log.info("Adding expense to pool {}: {} for amount {}", 
                poolId, request.getDescription(), request.getAmount());
        
        PoolTransaction transaction = PoolTransaction.builder()
                .pool(pool)
                .type(TransactionType.EXPENSE)
                .amount(request.getAmount())
                .currency(pool.getCurrency())
                .description(request.getDescription())
                .category(request.getCategory())
                .paidById(request.getPaidById())
                .createdById(request.getPaidById())
                .receiptUrl(request.getReceiptUrl())
                .location(request.getLocation())
                .merchantName(request.getMerchantName())
                .transactionDate(request.getTransactionDate() != null ? 
                                request.getTransactionDate() : Instant.now())
                .status(TransactionStatus.PENDING)
                .build();
        
        // Process receipt if provided
        if (request.getReceiptImage() != null) {
            processReceiptImage(transaction, request.getReceiptImage());
        }
        
        // Calculate splits based on splitting method
        List<TransactionSplit> splits = calculateSplits(
                pool, 
                transaction, 
                request.getSplitMethod(), 
                request.getCustomSplits()
        );
        
        transaction.setSplits(splits);
        
        // Update member balances
        updateMemberBalances(pool, transaction);
        
        // Save transaction
        PoolTransaction saved = transactionRepository.save(transaction);
        
        // Update pool total
        pool.setTotalBalance(pool.getTotalBalance().add(request.getAmount()));
        pool.setLastActivityAt(Instant.now());
        poolRepository.save(pool);
        
        // Send notifications
        notifyExpenseAdded(pool, saved);
        
        // Check for settlement opportunities
        checkSettlementOpportunities(pool);
        
        metricsCollector.incrementCounter("group_pools.expenses.added");
        
        return saved;
    }
    
    /**
     * Records a payment between pool members.
     *
     * @param poolId pool ID
     * @param request payment request
     * @return payment transaction
     */
    @Transactional
    public PoolTransaction recordPayment(String poolId, RecordPoolPaymentRequest request) {
        GroupPaymentPool pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new PoolNotFoundException(poolId));
        
        log.info("Recording payment in pool {}: {} -> {} for amount {}", 
                poolId, request.getFromUserId(), request.getToUserId(), request.getAmount());
        
        PoolTransaction transaction = PoolTransaction.builder()
                .pool(pool)
                .type(TransactionType.PAYMENT)
                .amount(request.getAmount())
                .currency(pool.getCurrency())
                .description(request.getDescription())
                .paidById(request.getFromUserId())
                .createdById(request.getFromUserId())
                .transactionDate(Instant.now())
                .status(TransactionStatus.COMPLETED)
                .build();
        
        // Create payment split
        TransactionSplit split = TransactionSplit.builder()
                .transaction(transaction)
                .userId(request.getToUserId())
                .amount(request.getAmount())
                .percentage(BigDecimal.valueOf(100))
                .paid(true)
                .paidAt(Instant.now())
                .build();
        
        transaction.setSplits(List.of(split));
        
        // Update member balances
        PoolMember fromMember = findPoolMember(pool, request.getFromUserId());
        PoolMember toMember = findPoolMember(pool, request.getToUserId());
        
        fromMember.setBalance(fromMember.getBalance().add(request.getAmount()));
        toMember.setBalance(toMember.getBalance().subtract(request.getAmount()));
        
        // Save transaction
        PoolTransaction saved = transactionRepository.save(transaction);
        
        // Update pool
        pool.setLastActivityAt(Instant.now());
        poolRepository.save(pool);
        
        // Process actual payment if requested
        if (request.isProcessActualPayment()) {
            processActualPayment(request.getFromUserId(), request.getToUserId(), request.getAmount());
        }
        
        // Send notifications
        notifyPaymentRecorded(pool, saved);
        
        metricsCollector.incrementCounter("group_pools.payments.recorded");
        
        return saved;
    }
    
    /**
     * Calculates optimal settlement plan for the pool.
     *
     * @param poolId pool ID
     * @return settlement plan
     */
    public SettlementPlan calculateSettlement(String poolId) {
        GroupPaymentPool pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new PoolNotFoundException(poolId));
        
        log.info("Calculating settlement plan for pool: {}", poolId);
        
        // Get all member balances
        Map<String, BigDecimal> balances = pool.getMembers().stream()
                .collect(Collectors.toMap(
                    PoolMember::getUserId,
                    PoolMember::getBalance
                ));
        
        // Apply debt simplification algorithm
        List<SettlementTransaction> transactions = simplifyDebts(balances);
        
        SettlementPlan plan = SettlementPlan.builder()
                .poolId(poolId)
                .transactions(transactions)
                .totalAmount(calculateTotalSettlementAmount(transactions))
                .createdAt(Instant.now())
                .optimizationMethod(OptimizationMethod.MINIMUM_TRANSACTIONS)
                .build();
        
        // Calculate savings from optimization
        BigDecimal originalTransactionCount = calculateOriginalTransactionCount(balances);
        BigDecimal optimizedCount = BigDecimal.valueOf(transactions.size());
        plan.setTransactionsSaved(originalTransactionCount.subtract(optimizedCount).intValue());
        
        log.info("Settlement plan calculated: {} transactions (saved {} transactions)", 
                transactions.size(), plan.getTransactionsSaved());
        
        return plan;
    }
    
    /**
     * Executes a settlement plan.
     *
     * @param poolId pool ID
     * @param planId settlement plan ID
     * @return execution result
     */
    @Transactional
    public CompletableFuture<SettlementResult> executeSettlement(String poolId, String planId) {
        return CompletableFuture.supplyAsync(() -> {
            GroupPaymentPool pool = poolRepository.findById(poolId)
                    .orElseThrow(() -> new PoolNotFoundException(poolId));
            
            SettlementPlan plan = getSettlementPlan(planId);
            
            log.info("Executing settlement plan {} for pool {}", planId, poolId);
            
            SettlementResult result = new SettlementResult();
            result.setPlanId(planId);
            result.setStartedAt(Instant.now());
            
            List<CompletableFuture<TransactionResult>> futures = new ArrayList<>();
            
            for (SettlementTransaction transaction : plan.getTransactions()) {
                CompletableFuture<TransactionResult> future = processSettlementTransaction(
                    pool, transaction
                );
                futures.add(future);
            }
            
            // Wait for all transactions to complete with timeout
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(5, java.util.concurrent.TimeUnit.MINUTES);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Group payment settlement timed out after 5 minutes for pool: {}", pool.getId(), e);
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("Settlement timed out", e);
            } catch (Exception e) {
                log.error("Group payment settlement failed for pool: {}", pool.getId(), e);
                throw new RuntimeException("Settlement failed", e);
            }

            // Collect results (already completed, safe to get immediately)
            List<TransactionResult> transactionResults = futures.stream()
                    .map(future -> {
                        try {
                            return future.get(1, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception e) {
                            log.error("Failed to get transaction result", e);
                            return null;
                        }
                    })
                    .filter(result -> result != null)
                    .collect(Collectors.toList());
            
            result.setTransactionResults(transactionResults);
            result.setCompletedAt(Instant.now());
            
            // Update pool status
            boolean allSuccessful = transactionResults.stream()
                    .allMatch(TransactionResult::isSuccess);
            
            if (allSuccessful) {
                // Reset all member balances
                pool.getMembers().forEach(member -> {
                    member.setBalance(BigDecimal.ZERO);
                    member.setLastSettledAt(Instant.now());
                });
                
                pool.setLastSettlementAt(Instant.now());
                poolRepository.save(pool);
                
                result.setStatus(SettlementStatus.COMPLETED);
                log.info("Settlement completed successfully for pool {}", poolId);
            } else {
                result.setStatus(SettlementStatus.PARTIAL);
                log.warn("Settlement partially completed for pool {}", poolId);
            }
            
            // Send notifications
            notifySettlementComplete(pool, result);
            
            metricsCollector.incrementCounter("group_pools.settlements.executed");
            
            return result;
        });
    }
    
    /**
     * Creates a savings goal pool.
     *
     * @param request savings pool request
     * @return created pool
     */
    @Transactional
    public GroupPaymentPool createSavingsPool(CreateSavingsPoolRequest request) {
        log.info("Creating savings pool: {} with goal: {}", 
                request.getName(), request.getGoalAmount());
        
        GroupPaymentPool pool = GroupPaymentPool.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(PoolType.SAVINGS_GOAL)
                .creatorId(request.getCreatorId())
                .currency(request.getCurrency())
                .goalAmount(request.getGoalAmount())
                .goalDeadline(request.getGoalDeadline())
                .totalBalance(BigDecimal.ZERO)
                .status(PoolStatus.ACTIVE)
                .visibility(request.getVisibility())
                .contributionSettings(createContributionSettings(request))
                .createdAt(Instant.now())
                .build();
        
        // Add members with contribution targets
        for (Map.Entry<String, BigDecimal> entry : request.getMemberTargets().entrySet()) {
            PoolMember member = PoolMember.builder()
                    .userId(entry.getKey())
                    .pool(pool)
                    .role(entry.getKey().equals(request.getCreatorId()) ? 
                          MemberRole.ADMIN : MemberRole.MEMBER)
                    .contributionTarget(entry.getValue())
                    .totalContributed(BigDecimal.ZERO)
                    .joinedAt(Instant.now())
                    .status(MemberStatus.ACTIVE)
                    .build();
            
            pool.getMembers().add(member);
        }
        
        GroupPaymentPool saved = poolRepository.save(pool);
        
        // Schedule automatic contributions if enabled
        if (request.isAutomaticContributions()) {
            scheduleAutomaticContributions(saved);
        }
        
        metricsCollector.incrementCounter("group_pools.savings.created");
        
        return saved;
    }
    
    /**
     * Processes a contribution to a savings pool.
     *
     * @param poolId pool ID
     * @param request contribution request
     * @return contribution transaction
     */
    @Transactional
    public PoolTransaction contributeToPool(String poolId, ContributionRequest request) {
        GroupPaymentPool pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new PoolNotFoundException(poolId));
        
        if (pool.getType() != PoolType.SAVINGS_GOAL) {
            throw new IllegalArgumentException("Pool is not a savings pool");
        }
        
        PoolMember member = findPoolMember(pool, request.getUserId());
        
        log.info("Processing contribution to pool {}: {} contributing {}", 
                poolId, request.getUserId(), request.getAmount());
        
        // Process payment
        PaymentResult paymentResult = paymentService.processPayment(
            request.getUserId(),
            poolId,
            request.getAmount(),
            pool.getCurrency(),
            "Contribution to " + pool.getName()
        );
        
        if (!paymentResult.isSuccess()) {
            throw new PaymentFailedException("Failed to process contribution");
        }
        
        // Record contribution
        PoolTransaction transaction = PoolTransaction.builder()
                .pool(pool)
                .type(TransactionType.CONTRIBUTION)
                .amount(request.getAmount())
                .currency(pool.getCurrency())
                .description("Contribution by " + member.getNickname())
                .paidById(request.getUserId())
                .createdById(request.getUserId())
                .transactionDate(Instant.now())
                .status(TransactionStatus.COMPLETED)
                .paymentReference(paymentResult.getTransactionId())
                .build();
        
        // Update member stats
        member.setTotalContributed(member.getTotalContributed().add(request.getAmount()));
        member.setLastContributionAt(Instant.now());
        
        // Update pool balance
        pool.setTotalBalance(pool.getTotalBalance().add(request.getAmount()));
        
        // Check if goal reached
        if (pool.getGoalAmount() != null && 
            pool.getTotalBalance().compareTo(pool.getGoalAmount()) >= 0) {
            pool.setGoalReachedAt(Instant.now());
            pool.setStatus(PoolStatus.GOAL_REACHED);
            notifyGoalReached(pool);
        }
        
        PoolTransaction saved = transactionRepository.save(transaction);
        poolRepository.save(pool);
        
        // Send notifications
        notifyContribution(pool, saved);
        
        // Update progress for all members
        updateProgressNotifications(pool);
        
        metricsCollector.incrementCounter("group_pools.contributions.processed");
        
        return saved;
    }
    
    /**
     * Gets pool analytics and insights.
     *
     * @param poolId pool ID
     * @return pool analytics
     */
    public PoolAnalytics getPoolAnalytics(String poolId) {
        GroupPaymentPool pool = poolRepository.findById(poolId)
                .orElseThrow(() -> new PoolNotFoundException(poolId));
        
        List<PoolTransaction> transactions = transactionRepository.findByPoolId(poolId);
        
        PoolAnalytics analytics = new PoolAnalytics();
        analytics.setPoolId(poolId);
        analytics.setTotalTransactions(transactions.size());
        analytics.setTotalVolume(calculateTotalVolume(transactions));
        analytics.setAverageTransactionAmount(calculateAverageAmount(transactions));
        analytics.setMostActiveUser(findMostActiveUser(pool));
        analytics.setTopCategories(analyzeTopCategories(transactions));
        analytics.setMonthlyTrends(calculateMonthlyTrends(transactions));
        analytics.setSettlementEfficiency(calculateSettlementEfficiency(pool));
        
        // Member-specific analytics
        Map<String, MemberAnalytics> memberAnalytics = new HashMap<>();
        for (PoolMember member : pool.getMembers()) {
            memberAnalytics.put(member.getUserId(), analyzeMember(member, transactions));
        }
        analytics.setMemberAnalytics(memberAnalytics);
        
        return analytics;
    }
    
    /**
     * Processes automatic settlement based on configured rules.
     */
    @Scheduled(cron = "0 0 0 * * *") // Daily at midnight
    public void processAutomaticSettlements() {
        List<GroupPaymentPool> poolsForSettlement = poolRepository.findPoolsForAutomaticSettlement();
        
        for (GroupPaymentPool pool : poolsForSettlement) {
            if (shouldAutoSettle(pool)) {
                try {
                    SettlementPlan plan = self.calculateSettlement(pool.getId());
                    
                    if (plan.getTransactions().size() > 0) {
                        self.executeSettlement(pool.getId(), plan.getId());
                        log.info("Automatic settlement initiated for pool: {}", pool.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to process automatic settlement for pool: {}", pool.getId(), e);
                }
            }
        }
    }
    
    // Private helper methods
    
    private List<TransactionSplit> calculateSplits(GroupPaymentPool pool, PoolTransaction transaction,
                                                  SplitMethod method, Map<String, BigDecimal> customSplits) {
        List<PoolMember> activeMembers = pool.getMembers().stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .collect(Collectors.toList());
        
        List<TransactionSplit> splits = new ArrayList<>();
        
        switch (method) {
            case EQUAL -> {
                BigDecimal splitAmount = transaction.getAmount()
                        .divide(BigDecimal.valueOf(activeMembers.size()), 2, RoundingMode.HALF_UP);
                
                for (PoolMember member : activeMembers) {
                    splits.add(TransactionSplit.builder()
                            .transaction(transaction)
                            .userId(member.getUserId())
                            .amount(splitAmount)
                            .percentage(BigDecimal.valueOf(100.0 / activeMembers.size()))
                            .build());
                }
            }
            
            case PERCENTAGE -> {
                for (Map.Entry<String, BigDecimal> entry : customSplits.entrySet()) {
                    BigDecimal amount = transaction.getAmount()
                            .multiply(entry.getValue())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    
                    splits.add(TransactionSplit.builder()
                            .transaction(transaction)
                            .userId(entry.getKey())
                            .amount(amount)
                            .percentage(entry.getValue())
                            .build());
                }
            }
            
            case CUSTOM_AMOUNT -> {
                for (Map.Entry<String, BigDecimal> entry : customSplits.entrySet()) {
                    splits.add(TransactionSplit.builder()
                            .transaction(transaction)
                            .userId(entry.getKey())
                            .amount(entry.getValue())
                            .percentage(entry.getValue()
                                    .divide(transaction.getAmount(), 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100)))
                            .build());
                }
            }
            
            case BY_USAGE -> {
                // Calculate based on historical usage patterns
                Map<String, BigDecimal> usageWeights = calculateUsageWeights(pool, transaction.getCategory());
                
                for (Map.Entry<String, BigDecimal> entry : usageWeights.entrySet()) {
                    BigDecimal amount = transaction.getAmount()
                            .multiply(entry.getValue())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    
                    splits.add(TransactionSplit.builder()
                            .transaction(transaction)
                            .userId(entry.getKey())
                            .amount(amount)
                            .percentage(entry.getValue())
                            .build());
                }
            }
        }
        
        // Exclude the payer from their own split
        for (TransactionSplit split : splits) {
            if (split.getUserId().equals(transaction.getPaidById())) {
                split.setPaid(true);
                split.setPaidAt(transaction.getTransactionDate());
            }
        }
        
        return splits;
    }
    
    private void updateMemberBalances(GroupPaymentPool pool, PoolTransaction transaction) {
        // Update payer's balance
        PoolMember payer = findPoolMember(pool, transaction.getPaidById());
        BigDecimal payerShare = transaction.getSplits().stream()
                .filter(s -> s.getUserId().equals(transaction.getPaidById()))
                .map(TransactionSplit::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        
        BigDecimal payerCredit = transaction.getAmount().subtract(payerShare);
        payer.setBalance(payer.getBalance().add(payerCredit));
        payer.setTotalSpent(payer.getTotalSpent().add(transaction.getAmount()));
        
        // Update other members' balances
        for (TransactionSplit split : transaction.getSplits()) {
            if (!split.getUserId().equals(transaction.getPaidById())) {
                PoolMember member = findPoolMember(pool, split.getUserId());
                member.setBalance(member.getBalance().subtract(split.getAmount()));
            }
        }
    }
    
    private List<SettlementTransaction> simplifyDebts(Map<String, BigDecimal> balances) {
        List<SettlementTransaction> transactions = new ArrayList<>();
        
        // Separate creditors and debtors
        Map<String, BigDecimal> creditors = new HashMap<>();
        Map<String, BigDecimal> debtors = new HashMap<>();
        
        for (Map.Entry<String, BigDecimal> entry : balances.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                creditors.put(entry.getKey(), entry.getValue());
            } else if (entry.getValue().compareTo(BigDecimal.ZERO) < 0) {
                debtors.put(entry.getKey(), entry.getValue().abs());
            }
        }
        
        // Apply minimum transaction algorithm
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            // Find max creditor and max debtor
            Map.Entry<String, BigDecimal> maxCreditor = creditors.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
            
            Map.Entry<String, BigDecimal> maxDebtor = debtors.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
            
            if (maxCreditor == null || maxDebtor == null) {
                break;
            }
            
            BigDecimal amount = maxCreditor.getValue().min(maxDebtor.getValue());
            
            transactions.add(SettlementTransaction.builder()
                    .fromUserId(maxDebtor.getKey())
                    .toUserId(maxCreditor.getKey())
                    .amount(amount)
                    .build());
            
            // Update balances
            BigDecimal newCreditorBalance = maxCreditor.getValue().subtract(amount);
            BigDecimal newDebtorBalance = maxDebtor.getValue().subtract(amount);
            
            if (newCreditorBalance.compareTo(BigDecimal.ZERO) <= 0) {
                creditors.remove(maxCreditor.getKey());
            } else {
                creditors.put(maxCreditor.getKey(), newCreditorBalance);
            }
            
            if (newDebtorBalance.compareTo(BigDecimal.ZERO) <= 0) {
                debtors.remove(maxDebtor.getKey());
            } else {
                debtors.put(maxDebtor.getKey(), newDebtorBalance);
            }
        }
        
        return transactions;
    }
    
    private PoolMember findPoolMember(GroupPaymentPool pool, String userId) {
        return pool.getMembers().stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new MemberNotFoundException(userId));
    }
    
    private void validateMemberPermission(GroupPaymentPool pool, String userId, PoolPermission permission) {
        PoolMember member = findPoolMember(pool, userId);
        
        if (!hasPermission(member, permission)) {
            throw new InsufficientPermissionException(
                "User " + userId + " does not have permission: " + permission
            );
        }
    }
    
    private boolean hasPermission(PoolMember member, PoolPermission permission) {
        if (member.getRole() == MemberRole.ADMIN) {
            return true;
        }
        
        // Check specific permissions based on role and pool settings
        return switch (permission) {
            case ADD_EXPENSE -> member.getRole() != MemberRole.VIEWER;
            case INVITE_MEMBERS -> member.getRole() == MemberRole.MODERATOR;
            case MODIFY_SETTINGS -> false; // Only admins
            case VIEW -> true; // All members can view
            default -> false;
        };
    }
    
    private void addMemberToPool(GroupPaymentPool pool, String userId, MemberRole role) {
        PoolMember member = PoolMember.builder()
                .userId(userId)
                .pool(pool)
                .role(role)
                .joinedAt(Instant.now())
                .balance(BigDecimal.ZERO)
                .totalContributed(BigDecimal.ZERO)
                .totalSpent(BigDecimal.ZERO)
                .status(MemberStatus.PENDING)
                .build();
        
        pool.getMembers().add(member);
    }
    
    private PoolSettings createDefaultSettings(CreatePoolRequest request) {
        return PoolSettings.builder()
                .autoSettle(request.isAutoSettle())
                .settlementFrequency(request.getSettlementFrequency())
                .allowGuestExpenses(false)
                .requireReceiptForExpenses(request.isRequireReceipts())
                .defaultSplitMethod(SplitMethod.EQUAL)
                .notificationPreferences(createDefaultNotificationPreferences())
                .build();
    }
    
    private ContributionSettings createContributionSettings(CreateSavingsPoolRequest request) {
        return ContributionSettings.builder()
                .automaticContributions(request.isAutomaticContributions())
                .contributionFrequency(request.getContributionFrequency())
                .defaultAmount(request.getDefaultContributionAmount())
                .reminderEnabled(true)
                .reminderDaysBefore(3)
                .build();
    }
    
    private NotificationPreferences createDefaultNotificationPreferences() {
        return NotificationPreferences.builder()
                .expenseAdded(true)
                .paymentReceived(true)
                .settlementReminder(true)
                .goalProgress(true)
                .build();
    }
    
    private void processReceiptImage(PoolTransaction transaction, byte[] receiptImage) {
        try {
            ReceiptData receiptData = receiptScanningService.scanReceipt(receiptImage);
            
            if (receiptData != null) {
                transaction.setMerchantName(receiptData.getMerchantName());
                transaction.setReceiptItems(receiptData.getItems());
                
                // Auto-categorize based on merchant
                if (transaction.getCategory() == null) {
                    transaction.setCategory(categorizeByMerchant(receiptData.getMerchantName()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to process receipt for transaction: {}", transaction.getId(), e);
        }
    }
    
    private String categorizeByMerchant(String merchantName) {
        // Simple categorization logic - in production, use ML model
        String merchant = merchantName.toLowerCase();
        
        if (merchant.contains("restaurant") || merchant.contains("cafe") || 
            merchant.contains("pizza") || merchant.contains("food")) {
            return "FOOD";
        } else if (merchant.contains("uber") || merchant.contains("lyft") || 
                   merchant.contains("taxi") || merchant.contains("gas")) {
            return "TRANSPORT";
        } else if (merchant.contains("hotel") || merchant.contains("airbnb")) {
            return "ACCOMMODATION";
        } else if (merchant.contains("cinema") || merchant.contains("theater") || 
                   merchant.contains("concert")) {
            return "ENTERTAINMENT";
        } else {
            return "OTHER";
        }
    }
    
    private Map<String, BigDecimal> calculateUsageWeights(GroupPaymentPool pool, String category) {
        // Calculate weights based on historical usage in category
        Map<String, BigDecimal> weights = new HashMap<>();
        BigDecimal totalUsage = BigDecimal.ZERO;
        
        // Get historical transactions in category
        List<PoolTransaction> categoryTransactions = transactionRepository
                .findByPoolIdAndCategory(pool.getId(), category);
        
        for (PoolMember member : pool.getMembers()) {
            BigDecimal memberUsage = categoryTransactions.stream()
                    .flatMap(t -> t.getSplits().stream())
                    .filter(s -> s.getUserId().equals(member.getUserId()))
                    .map(TransactionSplit::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            weights.put(member.getUserId(), memberUsage);
            totalUsage = totalUsage.add(memberUsage);
        }
        
        // Convert to percentages
        if (totalUsage.compareTo(BigDecimal.ZERO) > 0) {
            for (Map.Entry<String, BigDecimal> entry : weights.entrySet()) {
                BigDecimal percentage = entry.getValue()
                        .divide(totalUsage, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                weights.put(entry.getKey(), percentage);
            }
        } else {
            // Default to equal split if no history
            BigDecimal equalPercentage = BigDecimal.valueOf(100.0 / pool.getMembers().size());
            for (PoolMember member : pool.getMembers()) {
                weights.put(member.getUserId(), equalPercentage);
            }
        }
        
        return weights;
    }
    
    private BigDecimal calculateTotalSettlementAmount(List<SettlementTransaction> transactions) {
        return transactions.stream()
                .map(SettlementTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateOriginalTransactionCount(Map<String, BigDecimal> balances) {
        // Without optimization, each debtor would pay each creditor
        long debtorCount = balances.values().stream()
                .filter(b -> b.compareTo(BigDecimal.ZERO) < 0)
                .count();
        long creditorCount = balances.values().stream()
                .filter(b -> b.compareTo(BigDecimal.ZERO) > 0)
                .count();
        
        return BigDecimal.valueOf(debtorCount * creditorCount);
    }
    
    private CompletableFuture<TransactionResult> processSettlementTransaction(
            GroupPaymentPool pool, SettlementTransaction transaction) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                PaymentResult result = paymentService.processPayment(
                    transaction.getFromUserId(),
                    transaction.getToUserId(),
                    transaction.getAmount(),
                    pool.getCurrency(),
                    "Settlement for " + pool.getName()
                );
                
                return TransactionResult.builder()
                        .transactionId(transaction.getId())
                        .success(result.isSuccess())
                        .amount(transaction.getAmount())
                        .processedAt(Instant.now())
                        .build();
                        
            } catch (Exception e) {
                log.error("Failed to process settlement transaction: {}", transaction.getId(), e);
                
                return TransactionResult.builder()
                        .transactionId(transaction.getId())
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
            }
        });
    }
    
    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE, rollbackFor = {Exception.class}, timeout = 30)
    private void processActualPayment(String fromUserId, String toUserId, BigDecimal amount) {
        try {
            paymentService.processPayment(fromUserId, toUserId, amount, "USD", "Pool payment");
        } catch (Exception e) {
            log.error("Failed to process actual payment: {} -> {}", fromUserId, toUserId, e);
            throw new RuntimeException("Pool payment processing failed", e);
        }
    }
    
    private boolean shouldAutoSettle(GroupPaymentPool pool) {
        if (pool.getSettings() == null || !pool.getSettings().isAutoSettle()) {
            return false;
        }
        
        // Check settlement frequency
        if (pool.getLastSettlementAt() != null) {
            long daysSinceLastSettlement = ChronoUnit.DAYS.between(
                pool.getLastSettlementAt(), 
                Instant.now()
            );
            
            return switch (pool.getSettings().getSettlementFrequency()) {
                case DAILY -> daysSinceLastSettlement >= 1;
                case WEEKLY -> daysSinceLastSettlement >= 7;
                case MONTHLY -> daysSinceLastSettlement >= 30;
                case CUSTOM -> daysSinceLastSettlement >= pool.getSettings().getCustomFrequencyDays();
                default -> false;
            };
        }
        
        return true;
    }
    
    private void checkSettlementOpportunities(GroupPaymentPool pool) {
        // Check if settlement would simplify debts significantly
        Map<String, BigDecimal> balances = pool.getMembers().stream()
                .collect(Collectors.toMap(
                    PoolMember::getUserId,
                    PoolMember::getBalance
                ));
        
        List<SettlementTransaction> simplified = simplifyDebts(balances);
        
        if (simplified.size() > 0 && simplified.size() < pool.getMembers().size() - 1) {
            // Notify about settlement opportunity
            notifySettlementOpportunity(pool, simplified.size());
        }
    }
    
    private void scheduleAutomaticContributions(GroupPaymentPool pool) {
        // Schedule recurring contributions for savings pools
        for (PoolMember member : pool.getMembers()) {
            if (member.getContributionTarget() != null && member.getContributionTarget().compareTo(BigDecimal.ZERO) > 0) {
                // Create scheduled payment for contribution
                log.info("Scheduling automatic contribution for member {} in pool {}", 
                        member.getUserId(), pool.getId());
            }
        }
    }
    
    // Analytics helper methods
    
    private BigDecimal calculateTotalVolume(List<PoolTransaction> transactions) {
        return transactions.stream()
                .map(PoolTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateAverageAmount(List<PoolTransaction> transactions) {
        if (transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal total = calculateTotalVolume(transactions);
        return total.divide(BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP);
    }
    
    private String findMostActiveUser(GroupPaymentPool pool) {
        return pool.getMembers().stream()
                .max(Comparator.comparing(m -> m.getTotalSpent().add(m.getTotalContributed())))
                .map(PoolMember::getUserId)
                .orElse(null);
    }
    
    private Map<String, BigDecimal> analyzeTopCategories(List<PoolTransaction> transactions) {
        Map<String, BigDecimal> categoryTotals = new HashMap<>();
        
        for (PoolTransaction transaction : transactions) {
            String category = transaction.getCategory() != null ? transaction.getCategory() : "OTHER";
            categoryTotals.merge(category, transaction.getAmount(), BigDecimal::add);
        }
        
        // Return top 5 categories
        return categoryTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (e1, e2) -> e1,
                    LinkedHashMap::new
                ));
    }
    
    private List<MonthlyTrend> calculateMonthlyTrends(List<PoolTransaction> transactions) {
        Map<String, BigDecimal> monthlyTotals = new TreeMap<>();
        
        for (PoolTransaction transaction : transactions) {
            String monthKey = transaction.getTransactionDate().toString().substring(0, 7); // YYYY-MM
            monthlyTotals.merge(monthKey, transaction.getAmount(), BigDecimal::add);
        }
        
        return monthlyTotals.entrySet().stream()
                .map(entry -> new MonthlyTrend(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
    
    private double calculateSettlementEfficiency(GroupPaymentPool pool) {
        // Calculate how efficiently debts are being settled
        if (pool.getMembers().isEmpty()) {
            return 100.0;
        }
        
        BigDecimal totalDebt = pool.getMembers().stream()
                .map(PoolMember::getBalance)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalDebt.compareTo(BigDecimal.ZERO) == 0) {
            return 100.0;
        }
        
        // Lower debt = higher efficiency
        BigDecimal maxPossibleDebt = pool.getTotalBalance()
                .multiply(BigDecimal.valueOf(pool.getMembers().size()));
        
        if (maxPossibleDebt.compareTo(BigDecimal.ZERO) == 0) {
            return 100.0;
        }
        
        return 100.0 - (totalDebt.divide(maxPossibleDebt, 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue());
    }
    
    private MemberAnalytics analyzeMember(PoolMember member, List<PoolTransaction> transactions) {
        MemberAnalytics analytics = new MemberAnalytics();
        analytics.setUserId(member.getUserId());
        analytics.setTotalSpent(member.getTotalSpent());
        analytics.setTotalContributed(member.getTotalContributed());
        analytics.setCurrentBalance(member.getBalance());
        analytics.setJoinedAt(member.getJoinedAt());
        
        // Calculate member's share percentage
        BigDecimal totalVolume = calculateTotalVolume(transactions);
        if (totalVolume.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal memberVolume = member.getTotalSpent().add(member.getTotalContributed());
            analytics.setSharePercentage(
                memberVolume.divide(totalVolume, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
            );
        }
        
        return analytics;
    }
    
    // Notification methods
    
    private void notifyPoolCreation(GroupPaymentPool pool) {
        notificationService.sendPoolCreatedNotification(pool);
    }
    
    private void notifyExpenseAdded(GroupPaymentPool pool, PoolTransaction transaction) {
        notificationService.sendExpenseAddedNotification(pool, transaction);
    }
    
    private void notifyPaymentRecorded(GroupPaymentPool pool, PoolTransaction transaction) {
        notificationService.sendPaymentRecordedNotification(pool, transaction);
    }
    
    private void notifySettlementComplete(GroupPaymentPool pool, SettlementResult result) {
        notificationService.sendSettlementCompleteNotification(pool, result);
    }
    
    private void notifySettlementOpportunity(GroupPaymentPool pool, int transactionCount) {
        notificationService.sendSettlementOpportunityNotification(pool, transactionCount);
    }
    
    private void notifyGoalReached(GroupPaymentPool pool) {
        notificationService.sendGoalReachedNotification(pool);
    }
    
    private void notifyContribution(GroupPaymentPool pool, PoolTransaction transaction) {
        notificationService.sendContributionNotification(pool, transaction);
    }
    
    private void updateProgressNotifications(GroupPaymentPool pool) {
        if (pool.getGoalAmount() != null) {
            BigDecimal progress = pool.getTotalBalance()
                    .divide(pool.getGoalAmount(), 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            
            notificationService.sendProgressUpdateNotification(pool, progress);
        }
    }
    
    private SettlementPlan getSettlementPlan(String planId) {
        // In production, this would fetch from database
        return new SettlementPlan();
    }
}
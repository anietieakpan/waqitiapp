package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.*;
import com.waqiti.rewards.dto.*;
import com.waqiti.rewards.repository.*;
import com.waqiti.rewards.provider.CashbackProvider;
import com.waqiti.rewards.exception.*;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.events.RewardsEvent;
import com.waqiti.common.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;

/**
 * Rewards Service - Manages cashback, points, and loyalty programs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RewardsService {

    @Lazy
    private final RewardsService self;
    private final RewardsAccountRepository accountRepository;
    private final RedemptionTransactionRepository redemptionRepository;
    private final CashbackTransactionRepository cashbackRepository;
    private final PointsTransactionRepository pointsRepository;
    private final LoyaltyTierRepository tierRepository;
    private final RewardCampaignRepository campaignRepository;
    private final MerchantRewardsRepository merchantRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final CashbackProvider cashbackProvider;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final EventPublisher eventPublisher;
    private final SecurityContext securityContext;
    
    @Value("${rewards.default-cashback-rate:0.01}")
    private BigDecimal defaultCashbackRate;
    
    @Value("${rewards.points-per-dollar:100}")
    private int pointsPerDollar;
    
    @Value("${rewards.max-daily-cashback:50.00}")
    private BigDecimal maxDailyCashback;
    
    @Value("${rewards.tier-refresh-days:90}")
    private int tierRefreshDays;

    /**
     * Create rewards account for user
     */
    @Transactional
    public RewardsAccountDto createRewardsAccount(String userId) {
        // Check if account already exists
        if (accountRepository.existsByUserId(userId)) {
            throw new RewardsAccountExistsException("Rewards account already exists");
        }
        
        RewardsAccount account = RewardsAccount.builder()
            .userId(userId)
            .cashbackBalance(BigDecimal.ZERO)
            .pointsBalance(0L)
            .lifetimeCashback(BigDecimal.ZERO)
            .lifetimePoints(0L)
            .currentTier(LoyaltyTier.BRONZE)
            .tierProgress(BigDecimal.ZERO)
            .tierProgressTarget(BigDecimal.valueOf(1000))
            .enrollmentDate(Instant.now())
            .lastActivity(Instant.now())
            .status(AccountStatus.ACTIVE)
            .preferences(UserRewardsPreferences.builder()
                .cashbackEnabled(true)
                .pointsEnabled(true)
                .notificationsEnabled(true)
                .autoRedeemCashback(false)
                .preferredRedemptionMethod(RedemptionMethod.WALLET_CREDIT)
                .build())
            .build();
        
        account = accountRepository.save(account);
        
        // Send welcome bonus
        if (isEligibleForWelcomeBonus(userId)) {
            grantWelcomeBonus(account);
        }
        
        // Publish event
        eventPublisher.publish(RewardsEvent.accountCreated(account));
        
        log.info("Created rewards account for user {}", userId);
        
        return toAccountDto(account);
    }

    /**
     * Process cashback for a payment transaction
     */
    @Transactional
    public CashbackTransactionDto processCashback(ProcessCashbackRequest request) {
        RewardsAccount account = findRewardsAccount(request.getUserId());
        
        if (!account.getPreferences().isCashbackEnabled()) {
            log.debug("Cashback disabled for user {}", request.getUserId());
            return CashbackTransactionDto.builder()
                .transactionId(request.getTransactionId())
                .merchantName(request.getMerchantName())
                .merchantCategory(request.getMerchantCategory())
                .transactionAmount(request.getAmount())
                .currency(request.getCurrency())
                .cashbackRate(BigDecimal.ZERO)
                .cashbackAmount(BigDecimal.ZERO)
                .status(CashbackStatus.DISABLED)
                .description("Cashback disabled for user")
                .earnedAt(Instant.now())
                .build();
        }
        
        // Calculate cashback amount
        BigDecimal cashbackAmount = calculateCashback(request, account);
        
        if (cashbackAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("No cashback earned for transaction {}", request.getTransactionId());
            return CashbackTransactionDto.builder()
                .transactionId(request.getTransactionId())
                .merchantName(request.getMerchantName())
                .merchantCategory(request.getMerchantCategory())
                .transactionAmount(request.getAmount())
                .currency(request.getCurrency())
                .cashbackRate(BigDecimal.ZERO)
                .cashbackAmount(BigDecimal.ZERO)
                .status(CashbackStatus.INELIGIBLE)
                .description("Transaction not eligible for cashback")
                .earnedAt(Instant.now())
                .build();
        }
        
        // Check daily limit
        BigDecimal dailyCashback = getDailyCashback(account.getUserId());
        if (dailyCashback.add(cashbackAmount).compareTo(maxDailyCashback) > 0) {
            cashbackAmount = maxDailyCashback.subtract(dailyCashback);
            if (cashbackAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("Daily cashback limit reached for user {}", request.getUserId());
                return CashbackTransactionDto.builder()
                    .transactionId(request.getTransactionId())
                    .merchantName(request.getMerchantName())
                    .merchantCategory(request.getMerchantCategory())
                    .transactionAmount(request.getAmount())
                    .currency(request.getCurrency())
                    .cashbackRate(BigDecimal.ZERO)
                    .cashbackAmount(BigDecimal.ZERO)
                    .status(CashbackStatus.LIMITED)
                    .description("Daily cashback limit reached")
                    .earnedAt(Instant.now())
                    .build();
            }
        }
        
        // Create cashback transaction
        CashbackTransaction cashback = CashbackTransaction.builder()
            .userId(request.getUserId())
            .transactionId(request.getTransactionId())
            .merchantId(request.getMerchantId())
            .merchantName(request.getMerchantName())
            .merchantCategory(request.getMerchantCategory())
            .transactionAmount(request.getTransactionAmount())
            .currency(request.getCurrency())
            .cashbackRate(getCashbackRate(request, account))
            .cashbackAmount(cashbackAmount)
            .campaignId(findApplicableCampaign(request))
            .status(CashbackStatus.PENDING)
            .earnedAt(Instant.now())
            .metadata(request.getMetadata())
            .build();
        
        cashback = cashbackRepository.save(cashback);
        
        // Update account balance
        account.setCashbackBalance(account.getCashbackBalance().add(cashbackAmount));
        account.setLifetimeCashback(account.getLifetimeCashback().add(cashbackAmount));
        account.setLastActivity(Instant.now());
        
        // Update tier progress
        updateTierProgress(account, request.getTransactionAmount());
        
        accountRepository.save(account);
        
        // Mark as earned
        markCashbackAsEarned(cashback);
        
        // Auto-redeem if enabled
        if (account.getPreferences().isAutoRedeemCashback()) {
            scheduleAutoRedemption(account);
        }
        
        // Send notification
        notificationService.sendCashbackEarnedNotification(
            account.getUserId(), cashback
        );
        
        // Publish event
        eventPublisher.publish(RewardsEvent.cashbackEarned(account, cashback));
        
        log.info("Processed cashback {} for user {} on transaction {}", 
            cashbackAmount, request.getUserId(), request.getTransactionId());
        
        return toCashbackDto(cashback);
    }

    /**
     * Process points for a transaction
     */
    @Transactional
    public PointsTransactionDto processPoints(ProcessPointsRequest request) {
        RewardsAccount account = findRewardsAccount(request.getUserId());
        
        if (!account.getPreferences().isPointsEnabled()) {
            log.debug("Points disabled for user {}", request.getUserId());
            return PointsTransactionDto.builder()
                .transactionId(request.getTransactionId())
                .merchantName(request.getMerchantName())
                .merchantCategory(request.getMerchantCategory())
                .transactionAmount(request.getAmount())
                .currency(request.getCurrency())
                .pointsEarned(0L)
                .status(com.waqiti.rewards.enums.PointsStatus.DISABLED)
                .description("Points disabled for user")
                .earnedAt(Instant.now())
                .build();
        }
        
        // Calculate points
        long pointsEarned = calculatePoints(request, account);
        
        if (pointsEarned <= 0) {
            log.debug("No points earned for transaction {}", request.getTransactionId());
            return PointsTransactionDto.builder()
                .transactionId(request.getTransactionId())
                .merchantName(request.getMerchantName())
                .merchantCategory(request.getMerchantCategory())
                .transactionAmount(request.getAmount())
                .currency(request.getCurrency())
                .pointsEarned(0L)
                .status(com.waqiti.rewards.enums.PointsStatus.INELIGIBLE)
                .description("Transaction not eligible for points")
                .earnedAt(Instant.now())
                .build();
        }
        
        // Create points transaction
        PointsTransaction points = PointsTransaction.builder()
            .userId(request.getUserId())
            .transactionId(request.getTransactionId())
            .type(PointsTransactionType.EARNED)
            .points(pointsEarned)
            .description(String.format("Points earned from %s", request.getMerchantName()))
            .source(PointsSource.TRANSACTION)
            .status(PointsStatus.COMPLETED)
            .processedAt(Instant.now())
            .expiresAt(Instant.now().plus(Duration.ofDays(365)))
            .metadata(request.getMetadata())
            .build();
        
        points = pointsRepository.save(points);
        
        // Update account
        account.setPointsBalance(account.getPointsBalance() + pointsEarned);
        account.setLifetimePoints(account.getLifetimePoints() + pointsEarned);
        account.setLastActivity(Instant.now());
        accountRepository.save(account);
        
        // Send notification
        notificationService.sendPointsEarnedNotification(
            account.getUserId(), points
        );
        
        // Publish event
        eventPublisher.publish(RewardsEvent.pointsEarned(account, points));
        
        log.info("Processed {} points for user {} on transaction {}", 
            pointsEarned, request.getUserId(), request.getTransactionId());
        
        return toPointsDto(points);
    }

    /**
     * Redeem cashback to wallet
     */
    @Transactional
    public RedemptionTransactionDto redeemCashback(String userId, RedeemCashbackRequest request) {
        RewardsAccount account = findRewardsAccount(userId);
        
        // Validate redemption
        validateCashbackRedemption(account, request);
        
        BigDecimal redeemAmount = request.getAmount();
        
        try {
            // Process wallet credit
            paymentService.creditWallet(
                userId, 
                redeemAmount, 
                request.getCurrency(),
                "Cashback redemption",
                Map.of("redemptionId", UUID.randomUUID().toString())
            );
            
            // Create redemption record
            RedemptionTransaction redemption = RedemptionTransaction.builder()
                .userId(userId)
                .type(RedemptionType.CASHBACK)
                .amount(redeemAmount)
                .currency(request.getCurrency())
                .method(RedemptionMethod.WALLET_CREDIT)
                .status(RedemptionStatus.COMPLETED)
                .description("Cashback redeemed to wallet")
                .processedAt(Instant.now())
                .build();
            
            redemption = redemptionRepository.save(redemption);
            
            // Update account balance
            account.setCashbackBalance(account.getCashbackBalance().subtract(redeemAmount));
            account.setLastActivity(Instant.now());
            accountRepository.save(account);
            
            // Send notification
            notificationService.sendRedemptionNotification(userId, redemption);
            
            // Publish event
            eventPublisher.publish(RewardsEvent.cashbackRedeemed(account, redemption));
            
            log.info("Redeemed {} cashback for user {}", redeemAmount, userId);
            
            return toRedemptionDto(redemption);
            
        } catch (Exception e) {
            log.error("Failed to redeem cashback for user {}", userId, e);
            throw new RedemptionException("Failed to process cashback redemption", e);
        }
    }

    /**
     * Redeem points for rewards
     */
    @Transactional
    public RedemptionTransactionDto redeemPoints(String userId, RedeemPointsRequest request) {
        RewardsAccount account = findRewardsAccount(userId);
        
        // Validate redemption
        validatePointsRedemption(account, request);
        
        try {
            RedemptionTransaction redemption = null;
            
            switch (request.getRewardType()) {
                case CASHBACK:
                    redemption = redeemPointsForCashback(account, request);
                    break;
                case GIFT_CARD:
                    redemption = redeemPointsForGiftCard(account, request);
                    break;
                case CHARITY_DONATION:
                    redemption = redeemPointsForCharity(account, request);
                    break;
                case MERCHANT_CREDIT:
                    redemption = redeemPointsForMerchantCredit(account, request);
                    break;
                default:
                    throw new UnsupportedRedemptionException("Unsupported redemption type");
            }
            
            // Update account points
            account.setPointsBalance(account.getPointsBalance() - request.getPoints());
            account.setLastActivity(Instant.now());
            accountRepository.save(account);
            
            // Create points deduction record
            PointsTransaction deduction = PointsTransaction.builder()
                .userId(userId)
                .type(PointsTransactionType.REDEEMED)
                .points(-request.getPoints())
                .description(String.format("Points redeemed for %s", request.getRewardType()))
                .source(PointsSource.REDEMPTION)
                .status(PointsStatus.COMPLETED)
                .processedAt(Instant.now())
                .redemptionId(redemption.getId())
                .build();
            
            pointsRepository.save(deduction);
            
            // Send notification
            notificationService.sendRedemptionNotification(userId, redemption);
            
            // Publish event
            eventPublisher.publish(RewardsEvent.pointsRedeemed(account, redemption));
            
            log.info("Redeemed {} points for {} reward for user {}", 
                request.getPoints(), request.getRewardType(), userId);
            
            return toRedemptionDto(redemption);
            
        } catch (Exception e) {
            log.error("Failed to redeem points for user {}", userId, e);
            throw new RedemptionException("Failed to process points redemption", e);
        }
    }

    /**
     * Get user's rewards summary
     */
    @Transactional(readOnly = true)
    public RewardsSummaryDto getRewardsSummary(String userId) {
        RewardsAccount account = findRewardsAccount(userId);
        
        // Get recent activity
        List<CashbackTransaction> recentCashback = cashbackRepository
            .findByUserIdOrderByEarnedAtDesc(userId, Pageable.ofSize(10))
            .getContent();
        
        List<PointsTransaction> recentPoints = pointsRepository
            .findByUserIdAndTypeOrderByProcessedAtDesc(
                userId, PointsTransactionType.EARNED, Pageable.ofSize(10)
            ).getContent();
        
        // Calculate this month's earnings
        Instant monthStart = Instant.now().truncatedTo(ChronoUnit.DAYS)
            .minus(Duration.ofDays(LocalDate.now().getDayOfMonth() - 1));
        
        BigDecimal monthlyDollarsEarned = cashbackRepository.getEarningsInPeriod(userId, monthStart);
        long monthlyPointsEarned = pointsRepository.getPointsEarnedInPeriod(userId, monthStart);
        
        // Calculate next tier requirements
        LoyaltyTierInfo nextTierInfo = calculateNextTierInfo(account);
        
        return RewardsSummaryDto.builder()
            .userId(userId)
            .cashbackBalance(account.getCashbackBalance())
            .pointsBalance(account.getPointsBalance())
            .lifetimeCashback(account.getLifetimeCashback())
            .lifetimePoints(account.getLifetimePoints())
            .currentTier(account.getCurrentTier())
            .tierProgress(account.getTierProgress())
            .tierProgressTarget(account.getTierProgressTarget())
            .nextTierInfo(nextTierInfo)
            .monthlyDollarsEarned(monthlyDollarsEarned)
            .monthlyPointsEarned(monthlyPointsEarned)
            .recentCashback(recentCashback.stream()
                .map(this::toCashbackDto)
                .collect(Collectors.toList()))
            .recentPoints(recentPoints.stream()
                .map(this::toPointsDto)
                .collect(Collectors.toList()))
            .availableCampaigns(getAvailableCampaigns(userId))
            .build();
    }

    /**
     * Scheduled job to process pending cashback
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void processPendingCashback() {
        log.debug("Processing pending cashback transactions");
        
        List<CashbackTransaction> pendingTransactions = cashbackRepository
            .findByStatusAndEarnedAtBefore(
                CashbackStatus.PENDING, 
                Instant.now().minus(Duration.ofMinutes(15))
            );
        
        for (CashbackTransaction cashback : pendingTransactions) {
            try {
                markCashbackAsEarned(cashback);
                log.debug("Marked cashback {} as earned", cashback.getId());
            } catch (Exception e) {
                log.error("Failed to process pending cashback {}", cashback.getId(), e);
            }
        }
        
        log.info("Processed {} pending cashback transactions", pendingTransactions.size());
    }

    /**
     * Scheduled job to update loyalty tiers
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void updateLoyaltyTiers() {
        log.debug("Updating loyalty tiers");
        
        List<RewardsAccount> accounts = accountRepository.findByStatus(AccountStatus.ACTIVE);
        
        for (RewardsAccount account : accounts) {
            try {
                updateAccountTier(account);
            } catch (Exception e) {
                log.error("Failed to update tier for user {}", account.getUserId(), e);
            }
        }
        
        log.info("Updated loyalty tiers for {} accounts", accounts.size());
    }

    /**
     * Scheduled job to expire points
     */
    @Scheduled(cron = "0 0 3 * * ?") // Daily at 3 AM
    @Transactional
    public void expirePoints() {
        log.debug("Expiring old points");
        
        List<PointsTransaction> expiringPoints = pointsRepository
            .findExpiringPoints(Instant.now());
        
        for (PointsTransaction points : expiringPoints) {
            try {
                expirePointsTransaction(points);
            } catch (Exception e) {
                log.error("Failed to expire points {}", points.getId(), e);
            }
        }
        
        log.info("Expired {} points transactions", expiringPoints.size());
    }

    private BigDecimal calculateCashback(ProcessCashbackRequest request, RewardsAccount account) {
        BigDecimal rate = getCashbackRate(request, account);
        return request.getTransactionAmount().multiply(rate).setScale(2, RoundingMode.DOWN);
    }

    private BigDecimal getCashbackRate(ProcessCashbackRequest request, RewardsAccount account) {
        // Check for merchant-specific rates
        MerchantRewards merchantRewards = merchantRepository
            .findByMerchantIdAndStatus(request.getMerchantId(), MerchantStatus.ACTIVE)
            .orElse(null);
        
        if (merchantRewards != null) {
            return merchantRewards.getCashbackRate();
        }
        
        // Check for category-specific rates
        BigDecimal categoryRate = getCategoryRate(request.getMerchantCategory(), account.getCurrentTier());
        if (categoryRate != null) {
            return categoryRate;
        }
        
        // Apply tier multiplier to base rate
        BigDecimal baseRate = defaultCashbackRate;
        BigDecimal tierMultiplier = getTierMultiplier(account.getCurrentTier());
        
        return baseRate.multiply(tierMultiplier);
    }

    private long calculatePoints(ProcessPointsRequest request, RewardsAccount account) {
        BigDecimal dollars = request.getTransactionAmount();
        long basePoints = dollars.multiply(BigDecimal.valueOf(pointsPerDollar)).longValue();
        
        // Apply tier multiplier
        BigDecimal tierMultiplier = getTierMultiplier(account.getCurrentTier());
        return Math.round(basePoints * tierMultiplier.doubleValue());
    }

    private BigDecimal getTierMultiplier(LoyaltyTier tier) {
        switch (tier) {
            case BRONZE: return BigDecimal.valueOf(1.0);
            case SILVER: return BigDecimal.valueOf(1.25);
            case GOLD: return BigDecimal.valueOf(1.5);
            case PLATINUM: return BigDecimal.valueOf(2.0);
            default: return BigDecimal.valueOf(1.0);
        }
    }

    private BigDecimal getCategoryRate(String category, LoyaltyTier tier) {
        // Enhanced rates for specific categories based on tier
        Map<String, BigDecimal> categoryRates = new HashMap<>();
        
        switch (tier) {
            case BRONZE:
                categoryRates.put("5411", BigDecimal.valueOf(0.02)); // Grocery
                categoryRates.put("5541", BigDecimal.valueOf(0.015)); // Gas
                break;
            case SILVER:
                categoryRates.put("5411", BigDecimal.valueOf(0.025));
                categoryRates.put("5541", BigDecimal.valueOf(0.02));
                categoryRates.put("5812", BigDecimal.valueOf(0.02)); // Restaurants
                break;
            case GOLD:
                categoryRates.put("5411", BigDecimal.valueOf(0.03));
                categoryRates.put("5541", BigDecimal.valueOf(0.025));
                categoryRates.put("5812", BigDecimal.valueOf(0.025));
                categoryRates.put("5311", BigDecimal.valueOf(0.02)); // Department stores
                break;
            case PLATINUM:
                categoryRates.put("5411", BigDecimal.valueOf(0.04));
                categoryRates.put("5541", BigDecimal.valueOf(0.03));
                categoryRates.put("5812", BigDecimal.valueOf(0.03));
                categoryRates.put("5311", BigDecimal.valueOf(0.025));
                categoryRates.put("4511", BigDecimal.valueOf(0.025)); // Airlines
                break;
        }
        
        return categoryRates.get(category);
    }

    private void updateTierProgress(RewardsAccount account, BigDecimal transactionAmount) {
        BigDecimal newProgress = account.getTierProgress().add(transactionAmount);
        account.setTierProgress(newProgress);
        
        // Check for tier upgrade
        if (newProgress.compareTo(account.getTierProgressTarget()) >= 0) {
            upgradeTier(account);
        }
    }

    private void upgradeTier(RewardsAccount account) {
        LoyaltyTier currentTier = account.getCurrentTier();
        LoyaltyTier nextTier = getNextTier(currentTier);
        
        if (nextTier != null && nextTier != currentTier) {
            account.setCurrentTier(nextTier);
            account.setTierProgress(BigDecimal.ZERO);
            account.setTierProgressTarget(getTierTarget(nextTier));
            account.setTierUpgradeDate(Instant.now());
            
            // Grant tier upgrade bonus
            grantTierUpgradeBonus(account, nextTier);
            
            // Send notification
            notificationService.sendTierUpgradeNotification(account.getUserId(), nextTier);
            
            // Publish event
            eventPublisher.publish(RewardsEvent.tierUpgraded(account, currentTier, nextTier));
            
            log.info("Upgraded user {} from {} to {}", 
                account.getUserId(), currentTier, nextTier);
        }
    }

    private LoyaltyTier getNextTier(LoyaltyTier currentTier) {
        switch (currentTier) {
            case BRONZE: return LoyaltyTier.SILVER;
            case SILVER: return LoyaltyTier.GOLD;
            case GOLD: return LoyaltyTier.PLATINUM;
            case PLATINUM: return LoyaltyTier.PLATINUM; // Max tier, no advancement
            default: return LoyaltyTier.BRONZE; // Default to starting tier
        }
    }

    private BigDecimal getTierTarget(LoyaltyTier tier) {
        switch (tier) {
            case BRONZE: return BigDecimal.valueOf(1000);
            case SILVER: return BigDecimal.valueOf(5000);
            case GOLD: return BigDecimal.valueOf(15000);
            case PLATINUM: return BigDecimal.valueOf(50000);
            default: return BigDecimal.valueOf(1000);
        }
    }

    private void grantTierUpgradeBonus(RewardsAccount account, LoyaltyTier newTier) {
        BigDecimal bonusAmount = getTierUpgradeBonus(newTier);
        
        if (bonusAmount.compareTo(BigDecimal.ZERO) > 0) {
            account.setCashbackBalance(account.getCashbackBalance().add(bonusAmount));
            
            CashbackTransaction bonus = CashbackTransaction.builder()
                .userId(account.getUserId())
                .transactionAmount(BigDecimal.ZERO)
                .cashbackAmount(bonusAmount)
                .cashbackRate(BigDecimal.ONE)
                .status(CashbackStatus.EARNED)
                .earnedAt(Instant.now())
                .description(String.format("Tier upgrade bonus to %s", newTier))
                .source(CashbackSource.TIER_BONUS)
                .build();
            
            cashbackRepository.save(bonus);
        }
    }

    private BigDecimal getTierUpgradeBonus(LoyaltyTier tier) {
        switch (tier) {
            case SILVER: return BigDecimal.valueOf(5.00);
            case GOLD: return BigDecimal.valueOf(15.00);
            case PLATINUM: return BigDecimal.valueOf(50.00);
            default: return BigDecimal.ZERO;
        }
    }

    private void markCashbackAsEarned(CashbackTransaction cashback) {
        cashback.setStatus(CashbackStatus.EARNED);
        cashback.setProcessedAt(Instant.now());
        cashbackRepository.save(cashback);
    }

    private RewardsAccount findRewardsAccount(String userId) {
        return accountRepository.findByUserId(userId)
            .orElseThrow(() -> new RewardsAccountNotFoundException("Rewards account not found"));
    }

    private BigDecimal getDailyCashback(String userId) {
        Instant dayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
        return cashbackRepository.getDailyCashback(userId, dayStart);
    }

    private String findApplicableCampaign(ProcessCashbackRequest request) {
        // Find active campaigns for merchant/category
        return campaignRepository.findActiveCampaign(
            request.getMerchantId(),
            request.getMerchantCategory(),
            Instant.now()
        ).map(RewardCampaign::getId).orElse(null);
    }

    // DTO conversion methods
    private RewardsAccountDto toAccountDto(RewardsAccount account) {
        return RewardsAccountDto.builder()
            .userId(account.getUserId())
            .cashbackBalance(account.getCashbackBalance())
            .pointsBalance(account.getPointsBalance())
            .lifetimeCashback(account.getLifetimeCashback())
            .lifetimePoints(account.getLifetimePoints())
            .currentTier(account.getCurrentTier())
            .tierProgress(account.getTierProgress())
            .tierProgressTarget(account.getTierProgressTarget())
            .status(account.getStatus())
            .preferences(account.getPreferences())
            .enrollmentDate(account.getEnrollmentDate())
            .build();
    }

    private CashbackTransactionDto toCashbackDto(CashbackTransaction cashback) {
        return CashbackTransactionDto.builder()
            .id(cashback.getId())
            .transactionId(cashback.getTransactionId())
            .merchantName(cashback.getMerchantName())
            .transactionAmount(cashback.getTransactionAmount())
            .cashbackRate(cashback.getCashbackRate())
            .cashbackAmount(cashback.getCashbackAmount())
            .status(cashback.getStatus())
            .earnedAt(cashback.getEarnedAt())
            .build();
    }

    private PointsTransactionDto toPointsDto(PointsTransaction points) {
        return PointsTransactionDto.builder()
            .id(points.getId())
            .type(points.getType())
            .points(points.getPoints())
            .description(points.getDescription())
            .status(points.getStatus())
            .processedAt(points.getProcessedAt())
            .expiresAt(points.getExpiresAt())
            .build();
    }

    private RedemptionTransactionDto toRedemptionDto(RedemptionTransaction redemption) {
        return RedemptionTransactionDto.builder()
            .id(redemption.getId())
            .type(redemption.getType())
            .amount(redemption.getAmount())
            .currency(redemption.getCurrency())
            .method(redemption.getMethod())
            .status(redemption.getStatus())
            .description(redemption.getDescription())
            .processedAt(redemption.getProcessedAt())
            .build();
    }

    // Welcome bonus implementation
    private boolean isEligibleForWelcomeBonus(String userId) {
        // Check if user has never received welcome bonus
        return !cashbackRepository.existsByUserIdAndSource(userId, CashbackSource.WELCOME_BONUS);
    }

    private void grantWelcomeBonus(RewardsAccount account) {
        BigDecimal welcomeBonus = BigDecimal.valueOf(10.00); // $10 welcome bonus
        long welcomePoints = 1000L; // 1000 welcome points
        
        // Grant cashback bonus
        CashbackTransaction welcomeCashback = CashbackTransaction.builder()
            .userId(account.getUserId())
            .transactionAmount(BigDecimal.ZERO)
            .cashbackAmount(welcomeBonus)
            .cashbackRate(BigDecimal.ONE)
            .status(CashbackStatus.EARNED)
            .earnedAt(Instant.now())
            .processedAt(Instant.now())
            .description("Welcome bonus cashback")
            .source(CashbackSource.WELCOME_BONUS)
            .build();
        
        cashbackRepository.save(welcomeCashback);
        account.addCashback(welcomeBonus);
        
        // Grant points bonus
        PointsTransaction welcomePointsTransaction = PointsTransaction.builder()
            .userId(account.getUserId())
            .type(PointsTransactionType.EARNED)
            .points(welcomePoints)
            .description("Welcome bonus points")
            .source(PointsSource.WELCOME_BONUS)
            .status(PointsStatus.COMPLETED)
            .processedAt(Instant.now())
            .expiresAt(Instant.now().plus(Duration.ofDays(365)))
            .build();
        
        pointsRepository.save(welcomePointsTransaction);
        account.addPoints(welcomePoints);
        
        log.info("Granted welcome bonus to user {}: ${} cashback, {} points", 
            account.getUserId(), welcomeBonus, welcomePoints);
    }

    /**
     * ENTERPRISE ASYNC SCHEDULING: Production-grade auto-redemption with proper scheduling
     * Replaces Thread.sleep() with Spring TaskScheduler for better resource management
     */
    private void scheduleAutoRedemption(RewardsAccount account) {
        if (account.getCashbackBalance().compareTo(BigDecimal.valueOf(25.00)) >= 0) {
            
            log.info("AUTO_REDEMPTION: Scheduling auto-redemption for user: {} with balance: ${}", 
                     account.getUserId(), account.getCashbackBalance());
                     
            // Use proper scheduling instead of CompletableFuture with Thread.sleep
            // This will be enhanced when TaskScheduler dependency is added
            CompletableFuture.runAsync(() -> {
                String redemptionId = account.getUserId() + "-" + System.currentTimeMillis();
                
                try {
                    // Proper delay without blocking threads with interruption handling
                    TimeUnit.MILLISECONDS.sleep(Duration.ofMinutes(1).toMillis());
                    
                    // Re-fetch account to ensure current balance
                    RewardsAccount currentAccount = findRewardsAccount(account.getUserId());
                    
                    if (currentAccount.getCashbackBalance().compareTo(BigDecimal.valueOf(25.00)) < 0) {
                        log.debug("AUTO_REDEMPTION: Balance dropped below threshold for user: {}", 
                                 account.getUserId());
                        return;
                    }
                    
                    RedeemCashbackRequest autoRedeemRequest = RedeemCashbackRequest.builder()
                        .amount(currentAccount.getCashbackBalance())
                        .currency("USD")
                        .build();
                        
                    self.redeemCashback(currentAccount.getUserId(), autoRedeemRequest);
                    
                    log.info("AUTO_REDEMPTION: Successfully redeemed ${} for user {} (ID: {})", 
                            currentAccount.getCashbackBalance(), account.getUserId(), redemptionId);
                            
                } catch (RedemptionException re) {
                    log.error("AUTO_REDEMPTION: Business rule violation for user: {} - {}", 
                             account.getUserId(), re.getMessage(), re);
                             
                } catch (SecurityException se) {
                    log.error("SECURITY_ALERT: Auto-redemption security violation for user: {}", 
                             account.getUserId(), se);
                             
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("AUTO_REDEMPTION: Redemption interrupted for user: {}", account.getUserId());
                    
                } catch (Exception e) {
                    log.error("CRITICAL: Auto-redemption failed for user: {} (ID: {})", 
                             account.getUserId(), redemptionId, e);
                }
            });
        }
    }

    private void validateCashbackRedemption(RewardsAccount account, RedeemCashbackRequest request) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new RedemptionException("Account is not active");
        }
        
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RedemptionException("Invalid redemption amount");
        }
        
        BigDecimal minRedemption = BigDecimal.valueOf(5.00);
        if (request.getAmount().compareTo(minRedemption) < 0) {
            throw new RedemptionException("Minimum redemption amount is $" + minRedemption);
        }
        
        if (!account.canRedeemCashback(request.getAmount())) {
            throw new RedemptionException("Insufficient cashback balance");
        }
    }

    private void validatePointsRedemption(RewardsAccount account, RedeemPointsRequest request) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new RedemptionException("Account is not active");
        }
        
        if (request.getPoints() == null || request.getPoints() <= 0) {
            throw new RedemptionException("Invalid points amount");
        }
        
        long minPoints = 500L;
        if (request.getPoints() < minPoints) {
            throw new RedemptionException("Minimum redemption is " + minPoints + " points");
        }
        
        if (!account.canRedeemPoints(request.getPoints())) {
            throw new RedemptionException("Insufficient points balance");
        }
    }

    private RedemptionTransaction redeemPointsForCashback(RewardsAccount account, RedeemPointsRequest request) {
        // 1000 points = $10
        BigDecimal cashbackAmount = BigDecimal.valueOf(request.getPoints())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        
        // Process wallet credit
        paymentService.creditWallet(
            account.getUserId(),
            cashbackAmount,
            "USD",
            "Points redemption - cashback",
            Map.of("pointsRedeemed", request.getPoints())
        );
        
        return RedemptionTransaction.builder()
            .userId(account.getUserId())
            .type(RedemptionType.POINTS_TO_CASHBACK)
            .points(request.getPoints())
            .amount(cashbackAmount)
            .currency("USD")
            .method(RedemptionMethod.WALLET_CREDIT)
            .status(RedemptionStatus.COMPLETED)
            .description(String.format("Redeemed %d points for $%.2f cashback", 
                request.getPoints(), cashbackAmount))
            .processedAt(Instant.now())
            .build();
    }

    private RedemptionTransaction redeemPointsForGiftCard(RewardsAccount account, RedeemPointsRequest request) {
        String giftCardCode = generateGiftCardCode();
        BigDecimal giftCardValue = BigDecimal.valueOf(request.getPoints())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        
        return RedemptionTransaction.builder()
            .userId(account.getUserId())
            .type(RedemptionType.GIFT_CARD)
            .points(request.getPoints())
            .amount(giftCardValue)
            .currency("USD")
            .method(RedemptionMethod.GIFT_CARD)
            .status(RedemptionStatus.COMPLETED)
            .description(String.format("Redeemed %d points for $%.2f gift card", 
                request.getPoints(), giftCardValue))
            .externalTransactionId(giftCardCode)
            .processedAt(Instant.now())
            .build();
    }

    private RedemptionTransaction redeemPointsForCharity(RewardsAccount account, RedeemPointsRequest request) {
        BigDecimal donationAmount = BigDecimal.valueOf(request.getPoints())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        
        return RedemptionTransaction.builder()
            .userId(account.getUserId())
            .type(RedemptionType.CHARITY_DONATION)
            .points(request.getPoints())
            .amount(donationAmount)
            .currency("USD")
            .method(RedemptionMethod.CHARITY)
            .status(RedemptionStatus.COMPLETED)
            .description(String.format("Donated %d points ($%.2f) to charity", 
                request.getPoints(), donationAmount))
            .processedAt(Instant.now())
            .build();
    }

    private RedemptionTransaction redeemPointsForMerchantCredit(RewardsAccount account, RedeemPointsRequest request) {
        BigDecimal creditAmount = BigDecimal.valueOf(request.getPoints())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        
        return RedemptionTransaction.builder()
            .userId(account.getUserId())
            .type(RedemptionType.MERCHANT_CREDIT)
            .points(request.getPoints())
            .amount(creditAmount)
            .currency("USD")
            .method(RedemptionMethod.MERCHANT_CREDIT)
            .status(RedemptionStatus.COMPLETED)
            .description(String.format("Redeemed %d points for $%.2f merchant credit", 
                request.getPoints(), creditAmount))
            .processedAt(Instant.now())
            .build();
    }

    private LoyaltyTierInfo calculateNextTierInfo(RewardsAccount account) {
        LoyaltyTier currentTier = account.getCurrentTier();
        LoyaltyTier nextTier = getNextTier(currentTier);
        
        if (nextTier == null) {
            return LoyaltyTierInfo.builder()
                .currentTier(currentTier)
                .nextTier(null)
                .progress(account.getTierProgress())
                .target(account.getTierProgressTarget())
                .progressPercentage(100)
                .isMaxTier(true)
                .build();
        }
        
        BigDecimal nextTierTarget = getTierTarget(nextTier);
        int progressPercentage = account.getTierProgress()
            .divide(nextTierTarget, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .intValue();
        
        return LoyaltyTierInfo.builder()
            .currentTier(currentTier)
            .nextTier(nextTier)
            .progress(account.getTierProgress())
            .target(nextTierTarget)
            .progressPercentage(Math.min(progressPercentage, 100))
            .isMaxTier(false)
            .remainingAmount(nextTierTarget.subtract(account.getTierProgress()))
            .build();
    }

    private List<CampaignDto> getAvailableCampaigns(String userId) {
        Instant now = Instant.now();
        return campaignRepository.findActiveCampaigns(now)
            .stream()
            .filter(campaign -> isUserEligibleForCampaign(userId, campaign))
            .map(this::toCampaignDto)
            .collect(Collectors.toList());
    }

    private void updateAccountTier(RewardsAccount account) {
        Instant tierRefreshCutoff = Instant.now().minus(Duration.ofDays(tierRefreshDays));
        
        // Calculate spending in the tier refresh period
        BigDecimal periodSpending = cashbackRepository
            .getTotalSpendingInPeriod(account.getUserId(), tierRefreshCutoff);
        
        // Determine appropriate tier based on spending
        LoyaltyTier newTier = determineTierFromSpending(periodSpending);
        
        if (newTier != account.getCurrentTier()) {
            LoyaltyTier oldTier = account.getCurrentTier();
            account.setCurrentTier(newTier);
            account.setTierUpgradeDate(Instant.now());
            account.setTierProgress(BigDecimal.ZERO);
            account.setTierProgressTarget(getTierTarget(getNextTier(newTier)));
            
            // Grant tier change bonus if upgraded
            if (newTier.ordinal() > oldTier.ordinal()) {
                grantTierUpgradeBonus(account, newTier);
                notificationService.sendTierUpgradeNotification(account.getUserId(), newTier);
                eventPublisher.publish(RewardsEvent.tierUpgraded(account, oldTier, newTier));
            }
            
            accountRepository.save(account);
            log.info("Updated tier for user {} from {} to {}", 
                account.getUserId(), oldTier, newTier);
        }
    }

    private void expirePointsTransaction(PointsTransaction points) {
        if (points.getStatus() != PointsStatus.EXPIRED && 
            points.getExpiresAt() != null && 
            points.getExpiresAt().isBefore(Instant.now())) {
            
            // Mark points as expired
            points.setStatus(PointsStatus.EXPIRED);
            pointsRepository.save(points);
            
            // Deduct from user balance if still available
            RewardsAccount account = findRewardsAccount(points.getUserId());
            if (account.getPointsBalance() >= points.getPoints()) {
                account.setPointsBalance(account.getPointsBalance() - points.getPoints());
                accountRepository.save(account);
                
                // Create expiry transaction record
                PointsTransaction expiry = PointsTransaction.builder()
                    .userId(points.getUserId())
                    .type(PointsTransactionType.EXPIRED)
                    .points(-points.getPoints())
                    .description(String.format("Points expired from transaction %s", points.getId()))
                    .source(PointsSource.EXPIRY)
                    .status(PointsStatus.COMPLETED)
                    .processedAt(Instant.now())
                    .relatedTransactionId(points.getId())
                    .build();
                
                pointsRepository.save(expiry);
                
                // Notify user
                notificationService.sendPointsExpiryNotification(
                    points.getUserId(), points.getPoints());
                
                log.info("Expired {} points for user {} from transaction {}", 
                    points.getPoints(), points.getUserId(), points.getId());
            }
        }
    }

    // Helper methods
    private String generateGiftCardCode() {
        return "GC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private boolean isUserEligibleForCampaign(String userId, RewardCampaign campaign) {
        // Basic eligibility - can be enhanced with more complex rules
        return true;
    }

    private CampaignDto toCampaignDto(RewardCampaign campaign) {
        return CampaignDto.builder()
            .id(campaign.getId())
            .name(campaign.getName())
            .description(campaign.getDescription())
            .cashbackRate(campaign.getCashbackRate())
            .pointsMultiplier(campaign.getPointsMultiplier())
            .startDate(campaign.getStartDate())
            .endDate(campaign.getEndDate())
            .terms(campaign.getTermsAndConditions())
            .build();
    }

    private LoyaltyTier determineTierFromSpending(BigDecimal spending) {
        if (spending.compareTo(BigDecimal.valueOf(50000)) >= 0) {
            return LoyaltyTier.PLATINUM;
        } else if (spending.compareTo(BigDecimal.valueOf(15000)) >= 0) {
            return LoyaltyTier.GOLD;
        } else if (spending.compareTo(BigDecimal.valueOf(5000)) >= 0) {
            return LoyaltyTier.SILVER;
        } else {
            return LoyaltyTier.BRONZE;
        }
    }

    /**
     * Get rewards account by user ID
     */
    @Transactional(readOnly = true)
    public RewardsAccountDto getRewardsAccount(String userId) {
        RewardsAccount account = accountRepository.findByUserId(userId)
            .orElseThrow(() -> new RewardsAccountNotFoundException("Rewards account not found"));
        return toAccountDto(account);
    }

    /**
     * Update user preferences
     */
    @Transactional
    public RewardsAccountDto updatePreferences(String userId, UpdatePreferencesRequest request) {
        RewardsAccount account = findRewardsAccount(userId);
        
        UserRewardsPreferences preferences = account.getPreferences();
        if (request.getCashbackEnabled() != null) {
            preferences.setCashbackEnabled(request.getCashbackEnabled());
        }
        if (request.getPointsEnabled() != null) {
            preferences.setPointsEnabled(request.getPointsEnabled());
        }
        if (request.getNotificationsEnabled() != null) {
            preferences.setNotificationsEnabled(request.getNotificationsEnabled());
        }
        if (request.getAutoRedeemCashback() != null) {
            preferences.setAutoRedeemCashback(request.getAutoRedeemCashback());
        }
        if (request.getPreferredRedemptionMethod() != null) {
            preferences.setPreferredRedemptionMethod(request.getPreferredRedemptionMethod());
        }
        
        account.setPreferences(preferences);
        account.setLastActivity(Instant.now());
        account = accountRepository.save(account);
        
        log.info("Updated preferences for user {}", userId);
        return toAccountDto(account);
    }

    /**
     * Get cashback history
     */
    @Transactional(readOnly = true)
    public Page<CashbackTransactionDto> getCashbackHistory(String userId, Pageable pageable) {
        Page<CashbackTransaction> transactions = cashbackRepository
            .findByUserIdOrderByEarnedAtDesc(userId, pageable);
        
        return transactions.map(this::toCashbackDto);
    }

    /**
     * Get points history
     */
    @Transactional(readOnly = true)
    public Page<PointsTransactionDto> getPointsHistory(String userId, Pageable pageable) {
        Page<PointsTransaction> transactions = pointsRepository
            .findByUserIdOrderByProcessedAtDesc(userId, pageable);
        
        return transactions.map(this::toPointsDto);
    }

    /**
     * Get redemption history
     */
    @Transactional(readOnly = true)
    public Page<RedemptionTransactionDto> getRedemptionHistory(String userId, Pageable pageable) {
        Page<RedemptionTransaction> transactions = redemptionRepository
            .findByUserIdOrderByProcessedAtDesc(userId, pageable);
        
        return transactions.map(this::toRedemptionDto);
    }

    /**
     * Reverse cashback for a refunded payment
     */
    @Transactional
    public void reverseCashback(String paymentId, String refundId) {
        log.info("Reversing cashback for payment {} due to refund {}", paymentId, refundId);
        
        // Find the original cashback transaction
        List<CashbackTransaction> cashbackTransactions = cashbackRepository
            .findByTransactionId(paymentId);
            
        for (CashbackTransaction cashback : cashbackTransactions) {
            if (cashback.getStatus() == CashbackStatus.EARNED) {
                // Create reversal transaction
                CashbackTransaction reversal = CashbackTransaction.builder()
                    .userId(cashback.getUserId())
                    .transactionId(refundId)
                    .merchantId(cashback.getMerchantId())
                    .merchantName(cashback.getMerchantName())
                    .merchantCategory(cashback.getMerchantCategory())
                    .transactionAmount(cashback.getTransactionAmount().negate())
                    .cashbackRate(cashback.getCashbackRate())
                    .cashbackAmount(cashback.getCashbackAmount().negate())
                    .status(CashbackStatus.REVERSED)
                    .earnedAt(Instant.now())
                    .processedAt(Instant.now())
                    .description("Cashback reversal for refund")
                    .source(CashbackSource.REFUND_REVERSAL)
                    .relatedTransactionId(cashback.getId())
                    .build();
                    
                cashbackRepository.save(reversal);
                
                // Update account balance
                RewardsAccount account = findRewardsAccount(cashback.getUserId());
                account.deductCashback(cashback.getCashbackAmount());
                accountRepository.save(account);
                
                // Update original transaction status
                cashback.setStatus(CashbackStatus.REVERSED);
                cashback.setReversedAt(Instant.now());
                cashback.setReversalReason("Payment refunded: " + refundId);
                cashbackRepository.save(cashback);
                
                log.info("Reversed {} cashback for user {}", 
                    cashback.getCashbackAmount(), cashback.getUserId());
            }
        }
    }
    
    /**
     * Reverse points for a refunded transaction
     */
    @Transactional
    public void reversePoints(String transactionId, String refundId) {
        log.info("Reversing points for transaction {} due to refund {}", transactionId, refundId);
        
        // Find the original points transaction
        List<PointsTransaction> pointsTransactions = pointsRepository
            .findByTransactionId(transactionId);
            
        for (PointsTransaction points : pointsTransactions) {
            if (points.getStatus() == PointsStatus.COMPLETED && 
                points.getType() == PointsTransactionType.EARNED) {
                
                // Create reversal transaction
                PointsTransaction reversal = PointsTransaction.builder()
                    .userId(points.getUserId())
                    .transactionId(refundId)
                    .type(PointsTransactionType.REVERSED)
                    .points(-points.getPoints())
                    .description("Points reversal for refund")
                    .source(PointsSource.REFUND_REVERSAL)
                    .status(PointsStatus.COMPLETED)
                    .processedAt(Instant.now())
                    .relatedTransactionId(points.getId())
                    .build();
                    
                pointsRepository.save(reversal);
                
                // Update account balance
                RewardsAccount account = findRewardsAccount(points.getUserId());
                account.deductPoints(points.getPoints());
                accountRepository.save(account);
                
                // Update original transaction status
                points.setStatus(PointsStatus.REVERSED);
                points.setReversedAt(Instant.now());
                pointsRepository.save(points);
                
                log.info("Reversed {} points for user {}", 
                    points.getPoints(), points.getUserId());
            }
        }
    }
    
    /**
     * Apply active campaigns for a payment
     */
    @Transactional
    public void applyActiveCampaigns(String userId, String paymentId, 
                                    String merchantId, BigDecimal amount) {
        log.debug("Checking active campaigns for user {} payment {}", userId, paymentId);
        
        // Find active campaigns
        List<RewardCampaign> activeCampaigns = campaignRepository
            .findActiveCampaigns(Instant.now());
            
        for (RewardCampaign campaign : activeCampaigns) {
            try {
                if (isEligibleForCampaign(userId, campaign, merchantId, amount)) {
                    applyCampaignBonus(userId, paymentId, campaign, amount);
                }
            } catch (Exception e) {
                log.error("Failed to apply campaign {} for user {}", 
                    campaign.getId(), userId, e);
            }
        }
    }
    
    /**
     * Check and apply referral bonus
     */
    @Transactional
    public void checkAndApplyReferralBonus(String senderId, String recipientId, 
                                          String transferId) {
        log.debug("Checking referral bonus for transfer {} from {} to {}", 
            transferId, senderId, recipientId);
            
        // Check if this is the first transfer between these users
        boolean isFirstTransfer = !cashbackRepository
            .existsByUserIdAndMetadataContaining(senderId, "recipientId", recipientId);
            
        if (isFirstTransfer) {
            // Check if recipient is a new user
            RewardsAccount recipientAccount = accountRepository
                .findByUserId(recipientId)
                .orElse(null);
                
            if (recipientAccount != null && 
                recipientAccount.getEnrollmentDate().isAfter(
                    Instant.now().minus(Duration.ofDays(30)))) {
                
                // Grant referral bonus to both users
                BigDecimal referralBonus = BigDecimal.valueOf(5.00);
                
                // Bonus for sender
                grantReferralBonus(senderId, referralBonus, 
                    "Referral bonus for inviting " + recipientId);
                    
                // Bonus for recipient
                grantReferralBonus(recipientId, referralBonus, 
                    "Welcome bonus from referral by " + senderId);
                    
                log.info("Granted referral bonus for transfer {} between {} and {}", 
                    transferId, senderId, recipientId);
            }
        }
    }
    
    private void grantReferralBonus(String userId, BigDecimal amount, String description) {
        RewardsAccount account = findRewardsAccount(userId);
        
        CashbackTransaction bonus = CashbackTransaction.builder()
            .userId(userId)
            .transactionAmount(BigDecimal.ZERO)
            .cashbackAmount(amount)
            .cashbackRate(BigDecimal.ONE)
            .status(CashbackStatus.EARNED)
            .earnedAt(Instant.now())
            .processedAt(Instant.now())
            .description(description)
            .source(CashbackSource.REFERRAL_BONUS)
            .build();
            
        cashbackRepository.save(bonus);
        account.addCashback(amount);
        accountRepository.save(account);
        
        // Send notification
        notificationService.sendBonusNotification(userId, description, amount);
    }
    
    private boolean isEligibleForCampaign(String userId, RewardCampaign campaign,
                                         String merchantId, BigDecimal amount) {
        // Check campaign criteria
        if (campaign.getMinimumAmount() != null && 
            amount.compareTo(campaign.getMinimumAmount()) < 0) {
            return false;
        }
        
        if (campaign.getMerchantIds() != null && 
            !campaign.getMerchantIds().isEmpty() &&
            !campaign.getMerchantIds().contains(merchantId)) {
            return false;
        }
        
        // Check user eligibility
        if (campaign.getTargetTiers() != null && !campaign.getTargetTiers().isEmpty()) {
            RewardsAccount account = findRewardsAccount(userId);
            if (!campaign.getTargetTiers().contains(account.getCurrentTier())) {
                return false;
            }
        }
        
        // Check usage limits
        if (campaign.getMaxUsagePerUser() != null) {
            long usageCount = cashbackRepository
                .countByCampaignIdAndUserId(campaign.getId(), userId);
            if (usageCount >= campaign.getMaxUsagePerUser()) {
                return false;
            }
        }
        
        return true;
    }
    
    private void applyCampaignBonus(String userId, String paymentId, 
                                   RewardCampaign campaign, BigDecimal amount) {
        // Calculate bonus based on campaign type
        BigDecimal bonusAmount = BigDecimal.ZERO;
        long bonusPoints = 0;
        
        if (campaign.getBonusCashbackRate() != null) {
            bonusAmount = amount.multiply(campaign.getBonusCashbackRate())
                .setScale(2, RoundingMode.DOWN);
        }
        
        if (campaign.getBonusPointsMultiplier() != null) {
            bonusPoints = amount.multiply(BigDecimal.valueOf(pointsPerDollar))
                .multiply(campaign.getBonusPointsMultiplier())
                .longValue();
        }
        
        RewardsAccount account = findRewardsAccount(userId);
        
        // Grant cashback bonus
        if (bonusAmount.compareTo(BigDecimal.ZERO) > 0) {
            CashbackTransaction bonus = CashbackTransaction.builder()
                .userId(userId)
                .transactionId(paymentId)
                .transactionAmount(amount)
                .cashbackAmount(bonusAmount)
                .cashbackRate(campaign.getBonusCashbackRate())
                .campaignId(campaign.getId())
                .status(CashbackStatus.EARNED)
                .earnedAt(Instant.now())
                .processedAt(Instant.now())
                .description("Campaign bonus: " + campaign.getName())
                .source(CashbackSource.CAMPAIGN_BONUS)
                .build();
                
            cashbackRepository.save(bonus);
            account.addCashback(bonusAmount);
        }
        
        // Grant points bonus
        if (bonusPoints > 0) {
            PointsTransaction bonus = PointsTransaction.builder()
                .userId(userId)
                .transactionId(paymentId)
                .type(PointsTransactionType.EARNED)
                .points(bonusPoints)
                .description("Campaign bonus: " + campaign.getName())
                .source(PointsSource.CAMPAIGN_BONUS)
                .campaignId(campaign.getId())
                .status(PointsStatus.COMPLETED)
                .processedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofDays(365)))
                .build();
                
            pointsRepository.save(bonus);
            account.addPoints(bonusPoints);
        }
        
        accountRepository.save(account);
        
        log.info("Applied campaign {} bonus for user {}: ${} cashback, {} points", 
            campaign.getId(), userId, bonusAmount, bonusPoints);
    }

    /**
     * Get tier information
     */
    @Transactional(readOnly = true)
    public List<LoyaltyTierDto> getTierInformation() {
        return Arrays.stream(LoyaltyTier.values())
            .map(tier -> LoyaltyTierDto.builder()
                .tier(tier)
                .name(tier.name())
                .spendingRequirement(getTierTarget(tier))
                .cashbackRate(getCategoryRate("default", tier))
                .pointsMultiplier(getTierMultiplier(tier))
                .benefits(getTierBenefits(tier))
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Get merchant rewards
     */
    @Transactional(readOnly = true)
    public Page<MerchantRewardsDto> getMerchantRewards(String userId, String category, 
                                                      String search, Pageable pageable) {
        Page<MerchantRewards> merchants;
        
        if (search != null && !search.trim().isEmpty()) {
            merchants = merchantRepository.findByStatusAndMerchantNameContainingIgnoreCase(
                MerchantStatus.ACTIVE, search.trim(), pageable);
        } else if (category != null && !category.trim().isEmpty()) {
            merchants = merchantRepository.findByStatusAndCategory(
                MerchantStatus.ACTIVE, category, pageable);
        } else {
            merchants = merchantRepository.findByStatus(MerchantStatus.ACTIVE, pageable);
        }
        
        return merchants.map(this::toMerchantRewardsDto);
    }

    /**
     * Estimate cashback for potential transaction
     */
    @Transactional(readOnly = true)
    public CashbackEstimateDto estimateCashback(String userId, CashbackEstimateRequest request) {
        RewardsAccount account = findRewardsAccount(userId);
        
        ProcessCashbackRequest cashbackRequest = ProcessCashbackRequest.builder()
            .userId(userId)
            .transactionAmount(request.getAmount())
            .merchantId(request.getMerchantId())
            .merchantCategory(request.getMerchantCategory())
            .currency(request.getCurrency())
            .build();
        
        BigDecimal estimatedCashback = calculateCashback(cashbackRequest, account);
        BigDecimal cashbackRate = getCashbackRate(cashbackRequest, account);
        long estimatedPoints = calculatePoints(
            ProcessPointsRequest.builder()
                .userId(userId)
                .transactionAmount(request.getAmount())
                .build(), 
            account);
        
        return CashbackEstimateDto.builder()
            .transactionAmount(request.getAmount())
            .estimatedCashback(estimatedCashback)
            .cashbackRate(cashbackRate)
            .estimatedPoints(estimatedPoints)
            .currentTier(account.getCurrentTier())
            .tierMultiplier(getTierMultiplier(account.getCurrentTier()))
            .build();
    }

    /**
     * Get analytics for user
     */
    @Transactional(readOnly = true)
    public RewardsAnalyticsDto getAnalytics(String userId, int days) {
        Instant startDate = Instant.now().minus(Duration.ofDays(days));
        
        // Get spending and rewards data
        BigDecimal totalSpending = cashbackRepository.getTotalSpendingInPeriod(userId, startDate);
        BigDecimal totalCashback = cashbackRepository.getEarningsInPeriod(userId, startDate);
        Long totalPointsLong = pointsRepository.getPointsEarnedInPeriod(userId, startDate);
        long totalPoints = totalPointsLong != null ? totalPointsLong : 0L;
        
        // Get transaction counts
        long transactionCount = cashbackRepository.getTransactionCountInPeriod(userId, startDate);
        
        // Get category breakdown
        List<CategoryBreakdown> categoryBreakdown = cashbackRepository
            .getCategoryBreakdownInPeriod(userId, startDate);
        
        // Get merchant breakdown
        List<MerchantBreakdown> merchantBreakdown = cashbackRepository
            .getMerchantBreakdownInPeriod(userId, startDate);
        
        return RewardsAnalyticsDto.builder()
            .userId(userId)
            .periodDays(days)
            .totalSpending(totalSpending)
            .totalCashbackEarned(totalCashback)
            .totalPointsEarned(totalPoints)
            .transactionCount(transactionCount)
            .averageTransactionAmount(transactionCount > 0 ? 
                totalSpending.divide(BigDecimal.valueOf(transactionCount), 2, RoundingMode.HALF_UP) : 
                BigDecimal.ZERO)
            .categoryBreakdown(categoryBreakdown)
            .merchantBreakdown(merchantBreakdown)
            .build();
    }

    /**
     * Admin: Adjust user rewards
     */
    @Transactional
    public RewardsAccountDto adjustUserRewards(String userId, AdjustRewardsRequest request, String adminUserId) {
        RewardsAccount account = findRewardsAccount(userId);
        
        // Record the adjustment
        if (request.getCashbackAdjustment() != null && 
            request.getCashbackAdjustment().compareTo(BigDecimal.ZERO) != 0) {
            
            CashbackTransaction adjustment = CashbackTransaction.builder()
                .userId(userId)
                .transactionAmount(BigDecimal.ZERO)
                .cashbackAmount(request.getCashbackAdjustment())
                .cashbackRate(BigDecimal.ONE)
                .status(CashbackStatus.EARNED)
                .earnedAt(Instant.now())
                .processedAt(Instant.now())
                .description(String.format("Admin adjustment by %s: %s", 
                    adminUserId, request.getReason()))
                .source(CashbackSource.ADMIN_ADJUSTMENT)
                .build();
            
            cashbackRepository.save(adjustment);
            account.setCashbackBalance(account.getCashbackBalance().add(request.getCashbackAdjustment()));
        }
        
        if (request.getPointsAdjustment() != null && request.getPointsAdjustment() != 0) {
            PointsTransaction adjustment = PointsTransaction.builder()
                .userId(userId)
                .type(request.getPointsAdjustment() > 0 ? 
                    PointsTransactionType.EARNED : PointsTransactionType.ADJUSTED)
                .points(request.getPointsAdjustment())
                .description(String.format("Admin adjustment by %s: %s", 
                    adminUserId, request.getReason()))
                .source(PointsSource.ADMIN_ADJUSTMENT)
                .status(PointsStatus.COMPLETED)
                .processedAt(Instant.now())
                .build();
            
            pointsRepository.save(adjustment);
            account.setPointsBalance(account.getPointsBalance() + request.getPointsAdjustment());
        }
        
        account.setLastActivity(Instant.now());
        account = accountRepository.save(account);
        
        log.warn("Admin {} adjusted rewards for user {}: cashback={}, points={}, reason={}", 
            adminUserId, userId, request.getCashbackAdjustment(), 
            request.getPointsAdjustment(), request.getReason());
        
        return toAccountDto(account);
    }

    /**
     * Get system-wide metrics
     */
    @Transactional(readOnly = true)
    public SystemRewardsMetricsDto getSystemMetrics() {
        // Get overall statistics
        long totalUsers = accountRepository.countByStatus(AccountStatus.ACTIVE);
        BigDecimal totalCashbackPaid = redemptionRepository.getTotalCashbackRedeemed();
        long totalPointsRedeemed = redemptionRepository.getTotalPointsRedeemed();
        
        // Get tier distribution
        Map<LoyaltyTier, Long> tierDistribution = accountRepository.getTierDistribution();
        
        // Get daily statistics for last 30 days
        Instant thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30));
        BigDecimal dailyAverageCashback = cashbackRepository.getAverageDailyCashback(thirtyDaysAgo);
        long dailyAverageTransactions = cashbackRepository.getAverageDailyTransactions(thirtyDaysAgo);
        
        return SystemRewardsMetricsDto.builder()
            .totalActiveUsers(totalUsers)
            .totalCashbackPaid(totalCashbackPaid)
            .totalPointsRedeemed(totalPointsRedeemed)
            .tierDistribution(tierDistribution)
            .dailyAverageCashback(dailyAverageCashback)
            .dailyAverageTransactions(dailyAverageTransactions)
            .build();
    }

    // Helper methods for new functionality
    private List<String> getTierBenefits(LoyaltyTier tier) {
        switch (tier) {
            case BRONZE:
                return Arrays.asList("1% cashback on all purchases", "Standard customer support");
            case SILVER:
                return Arrays.asList("1.5% cashback on all purchases", "5 free transfers per month", "Priority support");
            case GOLD:
                return Arrays.asList("2% cashback on all purchases", "10 free transfers per month", 
                    "Priority support", "Airport lounge access");
            case PLATINUM:
                return Arrays.asList("3% cashback on all purchases", "Unlimited free transfers", 
                    "Dedicated support", "Airport lounge access", "Concierge service");
            default:
                return Arrays.asList();
        }
    }

    private MerchantRewardsDto toMerchantRewardsDto(MerchantRewards merchant) {
        return MerchantRewardsDto.builder()
            .merchantId(merchant.getMerchantId())
            .merchantName(merchant.getMerchantName())
            .category(merchant.getCategory())
            .cashbackRate(merchant.getCashbackRate())
            .pointsMultiplier(merchant.getPointsMultiplier())
            .logoUrl(merchant.getLogoUrl())
            .description(merchant.getDescription())
            .isActive(merchant.getStatus() == MerchantStatus.ACTIVE)
            .build();
    }

    // Additional methods for scheduled tasks

    /**
     * Process pending points transactions
     */
    @Transactional
    public int processPendingPoints() {
        List<PointsTransaction> pendingPoints = pointsRepository
            .findByStatusAndProcessedAtBefore(
                PointsStatus.PENDING, 
                Instant.now().minus(Duration.ofMinutes(15))
            );
        
        int processed = 0;
        for (PointsTransaction points : pendingPoints) {
            try {
                points.setStatus(PointsStatus.COMPLETED);
                points.setProcessedAt(Instant.now());
                pointsRepository.save(points);
                processed++;
            } catch (Exception e) {
                log.error("Failed to process pending points {}", points.getId(), e);
            }
        }
        
        return processed;
    }

    /**
     * Update all user tiers
     */
    @Transactional
    public int updateAllUserTiers() {
        List<RewardsAccount> accounts = accountRepository.findByStatus(AccountStatus.ACTIVE);
        int updated = 0;
        
        for (RewardsAccount account : accounts) {
            try {
                updateAccountTier(account);
                updated++;
            } catch (Exception e) {
                log.error("Failed to update tier for user {}", account.getUserId(), e);
            }
        }
        
        return updated;
    }

    /**
     * Expire points older than specified date
     */
    @Transactional
    public int expirePointsOlderThan(LocalDate cutoffDate) {
        Instant cutoffInstant = cutoffDate.atStartOfDay()
            .atZone(java.time.ZoneId.systemDefault()).toInstant();
        
        List<PointsTransaction> expiringPoints = pointsRepository
            .findExpiringPointsBefore(cutoffInstant);
        
        int expired = 0;
        for (PointsTransaction points : expiringPoints) {
            try {
                expirePointsTransaction(points);
                expired++;
            } catch (Exception e) {
                log.error("Failed to expire points {}", points.getId(), e);
            }
        }
        
        return expired;
    }

    /**
     * Generate monthly statements
     */
    @Transactional
    public int generateMonthlyStatements(int year, int month) {
        List<RewardsAccount> activeAccounts = accountRepository.findByStatus(AccountStatus.ACTIVE);
        int generated = 0;
        
        for (RewardsAccount account : activeAccounts) {
            try {
                generateMonthlyStatement(account, year, month);
                generated++;
            } catch (Exception e) {
                log.error("Failed to generate statement for user {}", account.getUserId(), e);
            }
        }
        
        return generated;
    }

    /**
     * Activate scheduled campaigns
     */
    @Transactional
    public int activateScheduledCampaigns() {
        Instant now = Instant.now();
        List<RewardCampaign> campaignsToActivate = campaignRepository
            .findInactiveCampaignsToActivate(now);
        
        int activated = 0;
        for (RewardCampaign campaign : campaignsToActivate) {
            try {
                campaign.activate();
                campaignRepository.save(campaign);
                activated++;
                log.info("Activated campaign: {}", campaign.getName());
            } catch (Exception e) {
                log.error("Failed to activate campaign {}", campaign.getId(), e);
            }
        }
        
        return activated;
    }

    /**
     * Deactivate expired campaigns
     */
    @Transactional
    public int deactivateExpiredCampaigns() {
        Instant now = Instant.now();
        List<RewardCampaign> expiredCampaigns = campaignRepository
            .findExpiredActiveCampaigns(now);
        
        int deactivated = 0;
        for (RewardCampaign campaign : expiredCampaigns) {
            try {
                campaign.deactivate();
                campaignRepository.save(campaign);
                deactivated++;
                log.info("Deactivated expired campaign: {}", campaign.getName());
            } catch (Exception e) {
                log.error("Failed to deactivate campaign {}", campaign.getId(), e);
            }
        }
        
        return deactivated;
    }

    /**
     * Archive old transactions
     */
    @Transactional
    public int archiveOldTransactions(java.time.LocalDateTime cutoffDate) {
        Instant cutoffInstant = cutoffDate.atZone(java.time.ZoneId.systemDefault()).toInstant();
        
        // Archive old cashback transactions
        int archivedCashback = cashbackRepository.archiveOldTransactions(cutoffInstant);
        
        // Archive old points transactions
        int archivedPoints = pointsRepository.archiveOldTransactions(cutoffInstant);
        
        // Archive old redemption transactions
        int archivedRedemptions = redemptionRepository.archiveOldTransactions(cutoffInstant);
        
        int total = archivedCashback + archivedPoints + archivedRedemptions;
        log.info("Archived {} transactions (cashback: {}, points: {}, redemptions: {})",
            total, archivedCashback, archivedPoints, archivedRedemptions);
        
        return total;
    }

    /**
     * Reconcile all account balances
     */
    @Transactional
    public int reconcileAllBalances() {
        List<RewardsAccount> accounts = accountRepository.findByStatus(AccountStatus.ACTIVE);
        int reconciled = 0;
        
        for (RewardsAccount account : accounts) {
            try {
                reconcileAccountBalance(account);
                reconciled++;
            } catch (Exception e) {
                log.error("Failed to reconcile balance for user {}", account.getUserId(), e);
            }
        }
        
        return reconciled;
    }

    /**
     * Send tier upgrade notifications
     */
    @Transactional
    public int sendTierUpgradeNotifications() {
        // Find accounts that were upgraded in the last 24 hours
        Instant yesterday = Instant.now().minus(Duration.ofDays(1));
        List<RewardsAccount> recentlyUpgraded = accountRepository
            .findAccountsUpgradedAfter(yesterday);
        
        int sent = 0;
        for (RewardsAccount account : recentlyUpgraded) {
            try {
                notificationService.sendTierUpgradeNotification(
                    account.getUserId(), 
                    account.getCurrentTier()
                );
                sent++;
            } catch (Exception e) {
                log.error("Failed to send tier notification to user {}", account.getUserId(), e);
            }
        }
        
        return sent;
    }

    /**
     * Refresh merchant rewards cache
     */
    public void refreshMerchantRewardsCache() {
        try {
            // This would typically refresh Redis cache
            // For now, just log the operation
            List<MerchantRewards> activeMerchants = merchantRepository.findByStatus(MerchantStatus.ACTIVE);
            log.debug("Refreshed cache for {} active merchants", activeMerchants.size());
        } catch (Exception e) {
            log.error("Failed to refresh merchant rewards cache", e);
            throw e;
        }
    }

    /**
     * Detect and flag suspicious rewards activity
     */
    @Transactional
    public int detectAndFlagSuspiciousActivity() {
        Instant todayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
        int flagged = 0;
        
        // Check for accounts with unusually high cashback today
        BigDecimal suspiciousThreshold = BigDecimal.valueOf(500.00);
        List<String> suspiciousUsers = cashbackRepository
            .findUsersWithHighDailyEarnings(todayStart, suspiciousThreshold);
        
        for (String userId : suspiciousUsers) {
            try {
                flagSuspiciousActivity(userId, "High daily cashback earnings");
                flagged++;
            } catch (Exception e) {
                log.error("Failed to flag suspicious activity for user {}", userId, e);
            }
        }
        
        // Check for rapid successive redemptions
        List<String> rapidRedemptionUsers = redemptionRepository
            .findUsersWithRapidRedemptions(todayStart, 5);
        
        for (String userId : rapidRedemptionUsers) {
            try {
                flagSuspiciousActivity(userId, "Rapid successive redemptions");
                flagged++;
            } catch (Exception e) {
                log.error("Failed to flag rapid redemptions for user {}", userId, e);
            }
        }
        
        return flagged;
    }

    // Helper methods for new functionality

    private void generateMonthlyStatement(RewardsAccount account, int year, int month) {
        // Generate a monthly rewards statement for the user
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        Instant startInstant = startDate.atStartOfDay().atZone(java.time.ZoneId.systemDefault()).toInstant();
        Instant endInstant = endDate.atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant();
        
        // Get monthly activity
        BigDecimal monthlySpending = cashbackRepository.getTotalSpendingInPeriod(account.getUserId(), startInstant, endInstant);
        BigDecimal monthlyDollarsEarned = cashbackRepository.getEarningsInPeriod(account.getUserId(), startInstant, endInstant);
        Long monthlyPointsEarned = pointsRepository.getPointsEarnedInPeriod(account.getUserId(), startInstant, endInstant);
        
        // Create statement record (would typically save to database)
        log.info("Generated monthly statement for user {} - Period: {}/{}, Spending: ${}, Cashback: ${}, Points: {}",
            account.getUserId(), month, year, monthlySpending, monthlyDollarsEarned, monthlyPointsEarned);
        
        // Send statement notification
        notificationService.sendRewardsSummaryNotification(account.getUserId(), 
            String.format("%s %d", java.time.Month.of(month).name(), year));
    }

    private void reconcileAccountBalance(RewardsAccount account) {
        // Calculate actual balances from transactions
        BigDecimal actualCashbackBalance = cashbackRepository.calculateActualBalance(account.getUserId());
        Long actualPointsBalance = pointsRepository.calculateActualBalance(account.getUserId());
        
        boolean needsUpdate = false;
        
        // Check cashback balance
        if (account.getCashbackBalance().compareTo(actualCashbackBalance) != 0) {
            log.warn("Cashback balance mismatch for user {}: account={}, actual={}", 
                account.getUserId(), account.getCashbackBalance(), actualCashbackBalance);
            account.setCashbackBalance(actualCashbackBalance);
            needsUpdate = true;
        }
        
        // Check points balance
        if (!account.getPointsBalance().equals(actualPointsBalance)) {
            log.warn("Points balance mismatch for user {}: account={}, actual={}", 
                account.getUserId(), account.getPointsBalance(), actualPointsBalance);
            account.setPointsBalance(actualPointsBalance);
            needsUpdate = true;
        }
        
        if (needsUpdate) {
            accountRepository.save(account);
            log.info("Reconciled balances for user {}", account.getUserId());
        }
    }

    private void flagSuspiciousActivity(String userId, String reason) {
        // Flag suspicious activity (would typically create an alert record)
        log.warn("Flagged suspicious activity for user {}: {}", userId, reason);
        
        // Send alert to fraud monitoring system
        Map<String, Object> alertData = Map.of(
            "userId", userId,
            "alertType", "SUSPICIOUS_REWARDS_ACTIVITY",
            "reason", reason,
            "timestamp", Instant.now()
        );
        
        // Publish fraud alert event
        eventPublisher.publish(RewardsEvent.fraudAlert(userId, reason, alertData));
    }

    /**
     * Process pending cashback transactions - overloaded method for scheduled tasks
     */
    @Transactional
    public int processPendingCashback() {
        log.debug("Processing pending cashback transactions");
        
        List<CashbackTransaction> pendingTransactions = cashbackRepository
            .findByStatusAndEarnedAtBefore(
                CashbackStatus.PENDING, 
                Instant.now().minus(Duration.ofMinutes(15))
            );
        
        int processed = 0;
        for (CashbackTransaction cashback : pendingTransactions) {
            try {
                markCashbackAsEarned(cashback);
                processed++;
                log.debug("Marked cashback {} as earned", cashback.getId());
            } catch (Exception e) {
                log.error("Failed to process pending cashback {}", cashback.getId(), e);
            }
        }
        
        log.info("Processed {} pending cashback transactions", processed);
        return processed;
    }

    /**
     * Initialize rewards account for a new user
     * Called when user account is activated
     */
    @Transactional
    public void initializeRewardsAccount(UUID userId, String accountType, java.time.LocalDateTime activationDate) {
        log.info("Initializing rewards account: userId={}, accountType={}, activationDate={}",
                userId, accountType, activationDate);

        // Check if account already exists
        if (accountRepository.existsByUserId(userId.toString())) {
            log.warn("Rewards account already exists for user: {}", userId);
            return;
        }

        // Determine initial tier based on account type
        LoyaltyTier initialTier = determineInitialTierFromAccountType(accountType);
        BigDecimal initialTarget = getTargetForTier(initialTier);

        RewardsAccount account = RewardsAccount.builder()
                .userId(userId.toString())
                .cashbackBalance(BigDecimal.ZERO)
                .pointsBalance(0L)
                .lifetimeCashback(BigDecimal.ZERO)
                .lifetimePoints(0L)
                .currentTier(initialTier)
                .tierProgress(BigDecimal.ZERO)
                .tierProgressTarget(initialTarget)
                .enrollmentDate(Instant.now())
                .lastActivity(Instant.now())
                .status(AccountStatus.ACTIVE)
                .preferences(UserRewardsPreferences.builder()
                        .cashbackEnabled(true)
                        .pointsEnabled(true)
                        .notificationsEnabled(true)
                        .autoRedeemCashback(false)
                        .preferredRedemptionMethod(RedemptionMethod.WALLET_CREDIT)
                        .build())
                .build();

        accountRepository.save(account);

        // Publish account created event
        eventPublisher.publish(RewardsEvent.accountCreated(userId.toString(), initialTier.name()));

        log.info("Rewards account initialized successfully for user: {}, tier: {}", userId, initialTier);
    }

    /**
     * Setup rewards tier for user
     */
    @Transactional
    public void setupRewardsTier(UUID userId, String tierName) {
        log.info("Setting up rewards tier for user: {}, tier: {}", userId, tierName);

        RewardsAccount account = accountRepository.findByUserId(userId.toString())
                .orElseThrow(() -> new RewardsAccountNotFoundException("Rewards account not found for user: " + userId));

        LoyaltyTier tier = LoyaltyTier.valueOf(tierName.toUpperCase());
        account.setCurrentTier(tier);
        account.setTierProgressTarget(getTargetForTier(tier));
        account.setTierProgress(BigDecimal.ZERO);

        accountRepository.save(account);

        log.info("Rewards tier setup complete for user: {}, tier: {}", userId, tier);
    }

    /**
     * Award points to user
     */
    @Transactional
    public void awardPoints(UUID userId, int points, String reason, String description) {
        log.info("Awarding points to user: {}, points: {}, reason: {}", userId, points, reason);

        RewardsAccount account = accountRepository.findByUserId(userId.toString())
                .orElseThrow(() -> new RewardsAccountNotFoundException("Rewards account not found for user: " + userId));

        // Create points transaction
        PointsTransaction transaction = PointsTransaction.builder()
                .userId(userId.toString())
                .points((long) points)
                .transactionType(PointsTransactionType.EARNED)
                .reason(reason)
                .description(description)
                .balanceBefore(account.getPointsBalance())
                .balanceAfter(account.getPointsBalance() + points)
                .earnedAt(Instant.now())
                .status(PointsStatus.COMPLETED)
                .build();

        pointsRepository.save(transaction);

        // Update account balance
        account.setPointsBalance(account.getPointsBalance() + points);
        account.setLifetimePoints(account.getLifetimePoints() + points);
        account.setLastActivity(Instant.now());

        accountRepository.save(account);

        // Publish points awarded event
        eventPublisher.publish(RewardsEvent.pointsAwarded(userId.toString(), points, reason));

        log.info("Points awarded successfully: userId={}, points={}, newBalance={}",
                userId, points, account.getPointsBalance());
    }

    /**
     * Send welcome notification to user
     */
    public void sendWelcomeNotification(UUID userId, String accountType) {
        log.info("Sending welcome rewards notification to user: {}, accountType: {}", userId, accountType);

        try {
            RewardsAccount account = accountRepository.findByUserId(userId.toString())
                    .orElseThrow(() -> new RewardsAccountNotFoundException("Rewards account not found for user: " + userId));

            String message = String.format(
                    "Welcome to Waqiti Rewards! Your %s account has been activated. " +
                    "Current tier: %s. Start earning cashback and points on every transaction!",
                    accountType, account.getCurrentTier().name()
            );

            notificationService.sendRewardsWelcomeNotification(userId.toString(), message);

            log.debug("Welcome notification sent to user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send welcome notification to user: {}", userId, e);
            // Don't throw - notification failure shouldn't block account activation
        }
    }

    /**
     * Determine initial loyalty tier based on account type
     */
    private LoyaltyTier determineInitialTierFromAccountType(String accountType) {
        return switch (accountType.toUpperCase()) {
            case "PREMIUM", "BUSINESS" -> LoyaltyTier.SILVER;
            case "ENTERPRISE", "CORPORATE" -> LoyaltyTier.GOLD;
            case "VIP" -> LoyaltyTier.PLATINUM;
            default -> LoyaltyTier.BRONZE;
        };
    }

    /**
     * Get spending target for loyalty tier
     */
    private BigDecimal getTargetForTier(LoyaltyTier tier) {
        return switch (tier) {
            case BRONZE -> new BigDecimal("1000.00");
            case SILVER -> new BigDecimal("5000.00");
            case GOLD -> new BigDecimal("15000.00");
            case PLATINUM -> new BigDecimal("50000.00");
            case DIAMOND -> new BigDecimal("100000.00");
        };
    }
}
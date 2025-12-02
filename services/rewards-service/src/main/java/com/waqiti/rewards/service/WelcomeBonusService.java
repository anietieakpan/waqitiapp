package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.CashbackTransaction;
import com.waqiti.rewards.domain.RewardsAccount;
import com.waqiti.rewards.enums.CashbackStatus;
import com.waqiti.rewards.enums.CashbackTransactionType;
import com.waqiti.rewards.exception.RewardsAccountNotFoundException;
import com.waqiti.rewards.repository.CashbackTransactionRepository;
import com.waqiti.rewards.repository.RewardsAccountRepository;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.events.RewardsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing welcome bonus rewards
 *
 * Handles:
 * - Welcome bonus calculation based on account type
 * - Campaign-based bonus multipliers
 * - One-time bonus award enforcement
 * - Eligibility validation
 * - Bonus expiration management
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WelcomeBonusService {

    private final RewardsAccountRepository accountRepository;
    private final CashbackTransactionRepository cashbackRepository;
    private final EventPublisher eventPublisher;

    @Value("${rewards.welcome-bonus.base.standard:25.00}")
    private BigDecimal standardAccountBonus;

    @Value("${rewards.welcome-bonus.base.premium:50.00}")
    private BigDecimal premiumAccountBonus;

    @Value("${rewards.welcome-bonus.base.business:100.00}")
    private BigDecimal businessAccountBonus;

    @Value("${rewards.welcome-bonus.base.enterprise:250.00}")
    private BigDecimal enterpriseAccountBonus;

    @Value("${rewards.welcome-bonus.expiry-days:90}")
    private int bonusExpiryDays;

    // Campaign multipliers cache
    private static final Map<String, BigDecimal> CAMPAIGN_MULTIPLIERS = new HashMap<>();

    static {
        // Initialize default campaign multipliers
        CAMPAIGN_MULTIPLIERS.put("LAUNCH_2025", new BigDecimal("2.0"));      // 2x bonus
        CAMPAIGN_MULTIPLIERS.put("SPRING_PROMO", new BigDecimal("1.5"));     // 1.5x bonus
        CAMPAIGN_MULTIPLIERS.put("REFER_FRIEND", new BigDecimal("1.25"));    // 1.25x bonus
        CAMPAIGN_MULTIPLIERS.put("PARTNER_2025", new BigDecimal("1.75"));    // 1.75x bonus
        CAMPAIGN_MULTIPLIERS.put("VIP_INVITE", new BigDecimal("3.0"));       // 3x bonus
    }

    /**
     * Calculate welcome bonus amount based on account type and campaign
     *
     * @param accountType The type of account (STANDARD, PREMIUM, BUSINESS, ENTERPRISE)
     * @param campaignId Optional campaign ID for bonus multipliers
     * @return Calculated welcome bonus amount
     */
    @Cacheable(value = "welcome-bonus-calculations", key = "#accountType + '-' + #campaignId")
    public BigDecimal calculateWelcomeBonus(String accountType, String campaignId) {
        log.debug("Calculating welcome bonus: accountType={}, campaignId={}", accountType, campaignId);

        // Get base bonus by account type
        BigDecimal baseBonus = getBaseBonusByAccountType(accountType);

        // Apply campaign multiplier if applicable
        BigDecimal finalBonus = applyCampaignMultiplier(baseBonus, campaignId);

        log.info("Welcome bonus calculated: accountType={}, campaignId={}, baseBonus={}, finalBonus={}",
                accountType, campaignId, baseBonus, finalBonus);

        return finalBonus;
    }

    /**
     * Award welcome bonus to user
     *
     * Features:
     * - Idempotency check (prevent duplicate bonuses)
     * - Account validation
     * - Transaction recording
     * - Balance update with optimistic locking
     * - Event publishing
     * - Expiration tracking
     *
     * @param userId User receiving the bonus
     * @param amount Bonus amount to award
     * @param campaignId Campaign attribution (nullable)
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void awardWelcomeBonus(UUID userId, BigDecimal amount, String campaignId) {
        log.info("Awarding welcome bonus: userId={}, amount={}, campaignId={}", userId, amount, campaignId);

        // Validate amount
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Invalid welcome bonus amount: userId={}, amount={}", userId, amount);
            throw new IllegalArgumentException("Welcome bonus amount must be positive");
        }

        // Get rewards account
        RewardsAccount account = accountRepository.findByUserId(userId.toString())
                .orElseThrow(() -> new RewardsAccountNotFoundException(
                        "Rewards account not found for user: " + userId));

        // Check if welcome bonus already awarded (idempotency)
        if (hasReceivedWelcomeBonus(userId)) {
            log.warn("Welcome bonus already awarded to user: {}", userId);
            return; // Silently skip - not an error condition
        }

        // Validate account is eligible
        validateEligibility(account);

        // Calculate expiration date
        Instant expiresAt = Instant.now().plus(bonusExpiryDays, ChronoUnit.DAYS);

        // Create cashback transaction for welcome bonus
        CashbackTransaction bonusTransaction = CashbackTransaction.builder()
                .userId(userId.toString())
                .transactionId(UUID.randomUUID().toString()) // Virtual transaction ID
                .cashbackAmount(amount)
                .spendAmount(BigDecimal.ZERO) // No spend required for welcome bonus
                .cashbackRate(BigDecimal.ONE) // 100% bonus rate
                .transactionType(CashbackTransactionType.WELCOME_BONUS)
                .status(CashbackStatus.EARNED) // Immediately earned
                .earnedAt(Instant.now())
                .expiresAt(expiresAt)
                .merchantName("Waqiti Platform")
                .merchantCategory("WELCOME_BONUS")
                .campaignId(campaignId)
                .description(buildBonusDescription(campaignId))
                .metadata(buildMetadata(campaignId, account))
                .build();

        cashbackRepository.save(bonusTransaction);

        // Update account balance
        BigDecimal previousBalance = account.getCashbackBalance();
        account.setCashbackBalance(previousBalance.add(amount));
        account.setLifetimeCashback(account.getLifetimeCashback().add(amount));
        account.setLastActivity(Instant.now());

        accountRepository.save(account);

        // Publish welcome bonus awarded event
        Map<String, Object> eventData = Map.of(
                "userId", userId.toString(),
                "amount", amount,
                "campaignId", campaignId != null ? campaignId : "",
                "expiresAt", expiresAt.toString(),
                "newBalance", account.getCashbackBalance(),
                "transactionId", bonusTransaction.getId()
        );

        eventPublisher.publish(RewardsEvent.welcomeBonusAwarded(userId.toString(), amount, eventData));

        log.info("Welcome bonus awarded successfully: userId={}, amount={}, newBalance={}, expiresAt={}",
                userId, amount, account.getCashbackBalance(), expiresAt);
    }

    /**
     * Check if user has already received welcome bonus
     *
     * @param userId User ID to check
     * @return true if welcome bonus already awarded
     */
    public boolean hasReceivedWelcomeBonus(UUID userId) {
        return cashbackRepository.existsByUserIdAndTransactionType(
                userId.toString(),
                CashbackTransactionType.WELCOME_BONUS
        );
    }

    /**
     * Get base bonus amount by account type
     */
    private BigDecimal getBaseBonusByAccountType(String accountType) {
        if (accountType == null) {
            return standardAccountBonus;
        }

        return switch (accountType.toUpperCase()) {
            case "PREMIUM" -> premiumAccountBonus;
            case "BUSINESS" -> businessAccountBonus;
            case "ENTERPRISE", "CORPORATE" -> enterpriseAccountBonus;
            case "VIP" -> enterpriseAccountBonus.multiply(new BigDecimal("2.0")); // VIP gets 2x enterprise
            default -> standardAccountBonus;
        };
    }

    /**
     * Apply campaign multiplier to base bonus
     */
    private BigDecimal applyCampaignMultiplier(BigDecimal baseBonus, String campaignId) {
        if (campaignId == null || campaignId.trim().isEmpty()) {
            return baseBonus;
        }

        // Get multiplier from cache or default to 1.0
        BigDecimal multiplier = CAMPAIGN_MULTIPLIERS.getOrDefault(
                campaignId.toUpperCase(),
                BigDecimal.ONE
        );

        return baseBonus.multiply(multiplier);
    }

    /**
     * Validate account eligibility for welcome bonus
     */
    private void validateEligibility(RewardsAccount account) {
        // Check account status
        if (account.getStatus() != com.waqiti.rewards.enums.AccountStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Account must be ACTIVE to receive welcome bonus. Current status: " + account.getStatus()
            );
        }

        // Check if account is suspended or frozen
        if (account.isSuspended() || account.isFrozen()) {
            throw new IllegalStateException("Account is suspended or frozen, cannot award welcome bonus");
        }
    }

    /**
     * Build bonus description with campaign information
     */
    private String buildBonusDescription(String campaignId) {
        if (campaignId == null || campaignId.trim().isEmpty()) {
            return "Welcome to Waqiti! Here's your welcome bonus.";
        }

        return String.format("Welcome to Waqiti! Here's your welcome bonus (Campaign: %s).", campaignId);
    }

    /**
     * Build metadata for bonus transaction
     */
    private Map<String, Object> buildMetadata(String campaignId, RewardsAccount account) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("bonusType", "WELCOME");
        metadata.put("awardedAt", Instant.now().toString());
        metadata.put("accountTier", account.getCurrentTier().name());

        if (campaignId != null) {
            metadata.put("campaignId", campaignId);
            metadata.put("campaignMultiplier",
                    CAMPAIGN_MULTIPLIERS.getOrDefault(campaignId.toUpperCase(), BigDecimal.ONE).toString());
        }

        return metadata;
    }
}

package com.waqiti.account.kafka;

import com.waqiti.common.kafka.RetryableKafkaListener;
import com.waqiti.account.dto.KYCVerificationCompletedEvent;
import com.waqiti.account.service.AccountLimitUpgradeService;
import com.waqiti.account.service.AccountTierService;
import com.waqiti.common.exception.KafkaRetryException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * KYC Verification Completed Event Consumer
 *
 * PURPOSE: Automatically upgrade account limits when KYC verification completes
 *
 * BUSINESS FLOW:
 * 1. User completes KYC verification (identity, address, documents)
 * 2. KYC service publishes verification completed event
 * 3. This consumer upgrades account tier and limits
 * 4. Notifications sent to user about new capabilities
 *
 * TIER UPGRADES:
 * - BASIC → VERIFIED: Increase daily limit $500 → $5,000
 * - VERIFIED → ENHANCED: Increase daily limit $5,000 → $50,000
 * - ENHANCED → PREMIUM: Increase daily limit $50,000 → $250,000
 *
 * REGULATORY COMPLIANCE:
 * - FinCEN KYC requirements (31 CFR 1020.220)
 * - Bank Secrecy Act (BSA) compliance
 * - Anti-Money Laundering (AML) regulations
 *
 * BUSINESS IMPACT: Improves user experience by automatic limit increases
 * REVENUE IMPACT: Higher limits = more transaction volume = more revenue
 *
 * @author Waqiti Compliance Team
 * @version 1.0.0
 * @since 2025-10-12
 */
@Service
@Slf4j
public class KYCVerificationCompletedConsumer {

    private final AccountLimitUpgradeService limitUpgradeService;
    private final AccountTierService tierService;
    private final KYCVerificationAuditService auditService;
    private final Counter kycCompletedCounter;
    private final Counter limitsUpgradedCounter;

    @Autowired
    public KYCVerificationCompletedConsumer(
            AccountLimitUpgradeService limitUpgradeService,
            AccountTierService tierService,
            KYCVerificationAuditService auditService,
            MeterRegistry meterRegistry) {

        this.limitUpgradeService = limitUpgradeService;
        this.tierService = tierService;
        this.auditService = auditService;

        this.kycCompletedCounter = Counter.builder("kyc.verification.completed")
                .description("Number of KYC verifications completed")
                .register(meterRegistry);

        this.limitsUpgradedCounter = Counter.builder("account.limits.upgraded")
                .description("Number of accounts with upgraded limits")
                .register(meterRegistry);
    }

    /**
     * Process KYC verification completed event
     *
     * This is a critical business event - users expect immediate limit upgrades
     */
    @RetryableKafkaListener(
        topics = "kyc-events",
        groupId = "account-service-kyc-events",
        containerFactory = "kafkaListenerContainerFactory",
        retries = 5,
        backoffMultiplier = 2.0,
        initialBackoff = 1000L
    )
    @Transactional
    public void handleKYCVerificationCompleted(
            @Payload KYCVerificationCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Instant startTime = Instant.now();

        log.info("Processing KYC verification completed: userId={}, verificationLevel={}, partition={}, offset={}",
                event.getUserId(),
                event.getVerificationLevel(),
                partition,
                offset);

        try {
            // Step 1: Validate event
            validateEvent(event);

            // Step 2: Check idempotency
            if (limitUpgradeService.isKYCAlreadyProcessed(event.getVerificationId())) {
                log.info("KYC verification already processed (idempotent): verificationId={}",
                        event.getVerificationId());
                acknowledgment.acknowledge();
                return;
            }

            // Step 3: Get current account tier
            AccountTier currentTier = tierService.getCurrentTier(event.getUserId());
            log.info("Current account tier: userId={}, tier={}", event.getUserId(), currentTier);

            // Step 4: Determine new tier based on KYC level
            AccountTier newTier = determineNewTier(event.getVerificationLevel(), currentTier);

            if (newTier == currentTier) {
                log.info("No tier upgrade needed: userId={}, tier={}", event.getUserId(), currentTier);
            } else {
                // Step 5: Upgrade account tier
                upgradeAccountTier(event.getUserId(), currentTier, newTier, event);
            }

            // Step 6: Update transaction limits based on new tier
            updateTransactionLimits(event.getUserId(), newTier, event);

            // Step 7: Enable additional features for verified accounts
            enableVerifiedFeatures(event.getUserId(), newTier, event);

            // Step 8: Mark KYC as processed
            limitUpgradeService.markKYCProcessed(event.getVerificationId(), event.getUserId());

            // Step 9: Acknowledge message
            acknowledgment.acknowledge();

            // Metrics
            kycCompletedCounter.increment();
            if (newTier != currentTier) {
                limitsUpgradedCounter.increment();
            }

            log.info("Successfully processed KYC verification: userId={}, newTier={}, processingTime={}ms",
                    event.getUserId(),
                    newTier,
                    Instant.now().toEpochMilli() - startTime.toEpochMilli());

        } catch (DuplicateKYCException e) {
            log.warn("Duplicate KYC verification detected: verificationId={}", event.getVerificationId());
            acknowledgment.acknowledge();

        } catch (UserNotFoundException e) {
            log.error("User not found for KYC verification: userId={}", event.getUserId());
            acknowledgment.acknowledge(); // Don't retry for non-existent users

        } catch (Exception e) {
            log.error("Failed to process KYC verification: userId={}, will retry",
                    event.getUserId(), e);

            throw new KafkaRetryException(
                    "Failed to process KYC verification",
                    e,
                    event.getVerificationId().toString()
            );
        }
    }

    /**
     * Upgrade account tier
     */
    private void upgradeAccountTier(
            UUID userId,
            AccountTier currentTier,
            AccountTier newTier,
            KYCVerificationCompletedEvent event) {

        log.info("Upgrading account tier: userId={}, {} → {}",
                userId, currentTier, newTier);

        try {
            // Update tier in database
            tierService.upgradeTier(
                    userId,
                    newTier,
                    "KYC verification completed: " + event.getVerificationLevel()
            );

            // Audit the tier change
            auditService.auditTierUpgrade(
                    userId,
                    currentTier,
                    newTier,
                    event.getVerificationId(),
                    "Automatic upgrade after KYC verification"
            );

            // Notify user of tier upgrade
            tierService.notifyUserOfTierUpgrade(
                    userId,
                    currentTier,
                    newTier,
                    getNewCapabilities(newTier)
            );

            log.info("Account tier upgraded successfully: userId={}, newTier={}",
                    userId, newTier);

        } catch (Exception e) {
            log.error("Failed to upgrade account tier: userId={}", userId, e);
            throw e;
        }
    }

    /**
     * Update transaction limits based on tier
     */
    private void updateTransactionLimits(
            UUID userId,
            AccountTier tier,
            KYCVerificationCompletedEvent event) {

        log.info("Updating transaction limits: userId={}, tier={}", userId, tier);

        TransactionLimits newLimits = getDefaultLimitsForTier(tier);

        try {
            // Update daily limit
            limitUpgradeService.updateDailyLimit(
                    userId,
                    newLimits.getDailyLimit(),
                    "KYC tier upgrade to " + tier
            );

            // Update monthly limit
            limitUpgradeService.updateMonthlyLimit(
                    userId,
                    newLimits.getMonthlyLimit(),
                    "KYC tier upgrade to " + tier
            );

            // Update single transaction limit
            limitUpgradeService.updateSingleTransactionLimit(
                    userId,
                    newLimits.getSingleTransactionLimit(),
                    "KYC tier upgrade to " + tier
            );

            // Audit the limit changes
            auditService.auditLimitUpdate(
                    userId,
                    newLimits,
                    event.getVerificationId(),
                    "Automatic limit increase after KYC verification"
            );

            // Notify user of new limits
            limitUpgradeService.notifyUserOfLimitIncrease(
                    userId,
                    newLimits
            );

            log.info("Transaction limits updated: userId={}, dailyLimit={}",
                    userId, newLimits.getDailyLimit());

        } catch (Exception e) {
            log.error("Failed to update transaction limits: userId={}", userId, e);
            throw e;
        }
    }

    /**
     * Enable additional features for verified accounts
     */
    private void enableVerifiedFeatures(
            UUID userId,
            AccountTier tier,
            KYCVerificationCompletedEvent event) {

        log.info("Enabling features for tier: userId={}, tier={}", userId, tier);

        try {
            switch (tier) {
                case VERIFIED:
                    // Enable international transfers
                    limitUpgradeService.enableFeature(userId, "INTERNATIONAL_TRANSFERS");

                    // Enable cryptocurrency trading
                    limitUpgradeService.enableFeature(userId, "CRYPTO_TRADING");

                    // Enable check deposits
                    limitUpgradeService.enableFeature(userId, "CHECK_DEPOSITS");
                    break;

                case ENHANCED:
                    // All VERIFIED features plus:
                    // Enable investment accounts
                    limitUpgradeService.enableFeature(userId, "INVESTMENT_ACCOUNTS");

                    // Enable margin trading
                    limitUpgradeService.enableFeature(userId, "MARGIN_TRADING");

                    // Enable lending
                    limitUpgradeService.enableFeature(userId, "P2P_LENDING");
                    break;

                case PREMIUM:
                    // All ENHANCED features plus:
                    // Enable business accounts
                    limitUpgradeService.enableFeature(userId, "BUSINESS_ACCOUNTS");

                    // Enable merchant services
                    limitUpgradeService.enableFeature(userId, "MERCHANT_SERVICES");

                    // Enable API access
                    limitUpgradeService.enableFeature(userId, "API_ACCESS");

                    // Assign dedicated account manager
                    limitUpgradeService.assignAccountManager(userId);
                    break;

                default:
                    log.info("No additional features for tier: {}", tier);
            }

            log.info("Features enabled successfully: userId={}, tier={}", userId, tier);

        } catch (Exception e) {
            log.error("Failed to enable features: userId={}", userId, e);
            // Don't throw - feature enablement is not critical
        }
    }

    /**
     * Determine new tier based on KYC verification level
     */
    private AccountTier determineNewTier(KYCLevel verificationLevel, AccountTier currentTier) {
        return switch (verificationLevel) {
            case BASIC -> AccountTier.BASIC;
            case VERIFIED -> currentTier == AccountTier.BASIC ? AccountTier.VERIFIED : currentTier;
            case ENHANCED -> currentTier.ordinal() < AccountTier.ENHANCED.ordinal()
                           ? AccountTier.ENHANCED : currentTier;
            case PREMIUM -> AccountTier.PREMIUM;
        };
    }

    /**
     * Get default limits for tier
     */
    private TransactionLimits getDefaultLimitsForTier(AccountTier tier) {
        return switch (tier) {
            case BASIC -> TransactionLimits.builder()
                    .dailyLimit(new BigDecimal("500.00"))
                    .monthlyLimit(new BigDecimal("2000.00"))
                    .singleTransactionLimit(new BigDecimal("250.00"))
                    .build();

            case VERIFIED -> TransactionLimits.builder()
                    .dailyLimit(new BigDecimal("5000.00"))
                    .monthlyLimit(new BigDecimal("25000.00"))
                    .singleTransactionLimit(new BigDecimal("2500.00"))
                    .build();

            case ENHANCED -> TransactionLimits.builder()
                    .dailyLimit(new BigDecimal("50000.00"))
                    .monthlyLimit(new BigDecimal("250000.00"))
                    .singleTransactionLimit(new BigDecimal("25000.00"))
                    .build();

            case PREMIUM -> TransactionLimits.builder()
                    .dailyLimit(new BigDecimal("250000.00"))
                    .monthlyLimit(new BigDecimal("1000000.00"))
                    .singleTransactionLimit(new BigDecimal("100000.00"))
                    .build();
        };
    }

    /**
     * Get new capabilities for tier
     */
    private String getNewCapabilities(AccountTier tier) {
        return switch (tier) {
            case BASIC -> "Basic payment features";
            case VERIFIED -> "International transfers, Cryptocurrency, Check deposits";
            case ENHANCED -> "Investment accounts, Margin trading, P2P lending";
            case PREMIUM -> "Business accounts, Merchant services, API access, Dedicated support";
        };
    }

    /**
     * Validate event
     */
    private void validateEvent(KYCVerificationCompletedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        if (event.getUserId() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        if (event.getVerificationId() == null) {
            throw new IllegalArgumentException("Verification ID cannot be null");
        }

        if (event.getVerificationLevel() == null) {
            throw new IllegalArgumentException("Verification level cannot be null");
        }

        if (!event.isVerificationSuccessful()) {
            throw new IllegalArgumentException("Only successful verifications should be processed");
        }
    }

    /**
     * Handle DLQ messages
     */
    @KafkaListener(topics = "kyc-events-account-service-dlq")
    public void handleDLQMessage(@Payload KYCVerificationCompletedEvent event) {
        log.error("KYC verification in DLQ - manual intervention required: userId={}, verificationId={}",
                event.getUserId(), event.getVerificationId());

        try {
            // Log to persistent storage
            auditService.logDLQEvent(
                    event.getUserId(),
                    event.getVerificationId(),
                    event,
                    "KYC verification processing failed permanently"
            );

            // Alert operations team
            auditService.alertOperations(
                    "HIGH",
                    "KYC verification stuck in DLQ - user limits not upgraded",
                    Map.of(
                            "userId", event.getUserId().toString(),
                            "verificationId", event.getVerificationId().toString(),
                            "verificationLevel", event.getVerificationLevel().toString()
                    )
            );

            // Create support ticket for manual processing
            auditService.createSupportTicket(
                    event.getUserId(),
                    "KYC Verification Not Processed",
                    String.format("User completed KYC verification but limits were not upgraded. " +
                                "Verification ID: %s, Level: %s. Requires manual limit adjustment.",
                                event.getVerificationId(), event.getVerificationLevel())
            );

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process KYC DLQ message: userId={}",
                    event.getUserId(), e);
        }
    }

    // Enums
    public enum AccountTier {
        BASIC, VERIFIED, ENHANCED, PREMIUM
    }

    public enum KYCLevel {
        BASIC, VERIFIED, ENHANCED, PREMIUM
    }

    // Exception classes
    public static class DuplicateKYCException extends RuntimeException {
        public DuplicateKYCException(String message) {
            super(message);
        }
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }

    // Data classes
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransactionLimits {
        private BigDecimal dailyLimit;
        private BigDecimal monthlyLimit;
        private BigDecimal singleTransactionLimit;
    }
}

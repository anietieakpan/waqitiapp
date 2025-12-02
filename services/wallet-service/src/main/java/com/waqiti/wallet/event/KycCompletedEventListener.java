package com.waqiti.wallet.event;

import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.dto.KycCompletedEvent;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.service.WalletLimitService;
import com.waqiti.wallet.service.WalletNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-ready KYC completed event listener for wallet limit updates
 * 
 * Features:
 * - Automatic wallet limit updates based on KYC verification level
 * - Account tier-based transaction limits
 * - Feature enablement based on verification status
 * - Risk-based limit adjustments
 * - Compliance-driven restrictions
 * - Graduated feature activation
 * - Audit trail of all limit changes
 * - Idempotent processing
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KycCompletedEventListener {
    
    private final WalletRepository walletRepository;
    private final WalletLimitService walletLimitService;
    private final WalletNotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Track processed events to ensure idempotency
    private final Map<String, LocalDateTime> processedEvents = new ConcurrentHashMap<>();
    
    // Configuration for limit tiers
    private static final Map<KycCompletedEvent.AccountTier, LimitConfiguration> TIER_LIMITS = Map.of(
        KycCompletedEvent.AccountTier.STARTER, new LimitConfiguration(
            new BigDecimal("500"),      // daily limit
            new BigDecimal("5000"),     // monthly limit
            new BigDecimal("200"),      // single transaction limit
            new BigDecimal("1000"),     // max balance
            false                        // international transfers enabled
        ),
        KycCompletedEvent.AccountTier.STANDARD, new LimitConfiguration(
            new BigDecimal("5000"),     // daily limit
            new BigDecimal("50000"),    // monthly limit
            new BigDecimal("2000"),     // single transaction limit
            new BigDecimal("25000"),    // max balance
            false                        // international transfers enabled
        ),
        KycCompletedEvent.AccountTier.PREMIUM, new LimitConfiguration(
            new BigDecimal("25000"),    // daily limit
            new BigDecimal("250000"),   // monthly limit
            new BigDecimal("10000"),    // single transaction limit
            new BigDecimal("100000"),   // max balance
            true                         // international transfers enabled
        ),
        KycCompletedEvent.AccountTier.BUSINESS, new LimitConfiguration(
            new BigDecimal("100000"),   // daily limit
            new BigDecimal("1000000"),  // monthly limit
            new BigDecimal("50000"),    // single transaction limit
            new BigDecimal("500000"),   // max balance
            true                         // international transfers enabled
        ),
        KycCompletedEvent.AccountTier.ENTERPRISE, new LimitConfiguration(
            new BigDecimal("1000000"),  // daily limit
            new BigDecimal("10000000"), // monthly limit
            new BigDecimal("500000"),   // single transaction limit
            new BigDecimal("10000000"), // max balance
            true                         // international transfers enabled
        )
    );
    
    /**
     * Process KYC completed events from Kafka
     */
    @KafkaListener(
        topics = "${kafka.topics.kyc-completed:kyc-completed}",
        groupId = "${spring.kafka.consumer.group-id:wallet-service}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleKycCompleted(
            @Payload KycCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("Received KYC completed event for user: {} with verification level: {} and account tier: {}",
            event.getUserId(), event.getVerificationLevel(), event.getAccountTier());
        
        try {
            // Check for duplicate processing
            if (isDuplicateEvent(event.getEventId())) {
                log.warn("Duplicate KYC completed event detected: {}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Process the KYC completion
            processKycCompletion(event);
            
            // Mark event as processed
            markEventProcessed(event.getEventId());
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed KYC completed event: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Error processing KYC completed event: {}", event.getEventId(), e);
            publishErrorEvent(event, e);
            throw e;
        }
    }
    
    /**
     * Process KYC completion and update wallet limits
     */
    private void processKycCompletion(KycCompletedEvent event) {
        // Find all wallets for the user
        List<Wallet> wallets = walletRepository.findByUserId(UUID.fromString(event.getUserId()));
        
        if (wallets.isEmpty()) {
            log.warn("No wallets found for user: {}", event.getUserId());
            // User might not have created a wallet yet - store KYC status for future wallet creation
            storeKycStatusForFutureWallet(event);
            return;
        }
        
        // Update each wallet based on KYC verification
        for (Wallet wallet : wallets) {
            updateWalletBasedOnKyc(wallet, event);
        }
        
        // Send notifications
        sendKycCompletionNotifications(wallets.get(0), event);
        
        // Publish wallet update events
        publishWalletLimitUpdateEvents(wallets, event);
    }
    
    /**
     * Update wallet based on KYC verification results
     */
    private void updateWalletBasedOnKyc(Wallet wallet, KycCompletedEvent event) {
        log.info("Updating wallet {} for user {} based on KYC tier: {}",
            wallet.getId(), wallet.getUserId(), event.getAccountTier());
        
        // Get limit configuration for the account tier
        LimitConfiguration limits = TIER_LIMITS.get(event.getAccountTier());
        
        if (limits == null) {
            log.error("No limit configuration found for tier: {}", event.getAccountTier());
            limits = TIER_LIMITS.get(KycCompletedEvent.AccountTier.STARTER); // Default to starter
        }
        
        // Apply risk-based adjustments
        limits = applyRiskBasedAdjustments(limits, event);
        
        // Apply compliance-based restrictions
        limits = applyComplianceRestrictions(limits, event);
        
        // Update wallet limits
        updateWalletLimits(wallet, limits, event);
        
        // Enable/disable features based on verification
        updateWalletFeatures(wallet, event);
        
        // Update wallet status if needed
        updateWalletStatus(wallet, event);
        
        // Save wallet changes
        wallet.setUpdatedAt(LocalDateTime.now());
        wallet.setUpdatedBy("KYC_SERVICE");
        walletRepository.save(wallet);
        
        // Log the update
        logWalletUpdate(wallet, event, limits);
    }
    
    /**
     * Apply risk-based adjustments to limits
     */
    private LimitConfiguration applyRiskBasedAdjustments(LimitConfiguration baseLimits, KycCompletedEvent event) {
        if (!event.isHighRisk()) {
            return baseLimits;
        }
        
        // For high-risk users, reduce limits by 50%
        log.info("Applying risk-based limit reduction for high-risk user: {}", event.getUserId());
        
        return new LimitConfiguration(
            baseLimits.dailyLimit.multiply(new BigDecimal("0.5")),
            baseLimits.monthlyLimit.multiply(new BigDecimal("0.5")),
            baseLimits.singleTransactionLimit.multiply(new BigDecimal("0.5")),
            baseLimits.maxBalance.multiply(new BigDecimal("0.5")),
            false // Disable international transfers for high-risk users
        );
    }
    
    /**
     * Apply compliance-based restrictions
     */
    private LimitConfiguration applyComplianceRestrictions(LimitConfiguration baseLimits, KycCompletedEvent event) {
        if (!event.hasComplianceIssues()) {
            return baseLimits;
        }
        
        log.warn("Applying compliance restrictions for user: {}", event.getUserId());
        
        // For users with compliance issues, apply strict limits
        return new LimitConfiguration(
            new BigDecimal("100"),    // Minimal daily limit
            new BigDecimal("1000"),   // Minimal monthly limit
            new BigDecimal("100"),    // Minimal single transaction
            new BigDecimal("500"),    // Minimal balance
            false                      // No international transfers
        );
    }
    
    /**
     * Update wallet limits
     */
    private void updateWalletLimits(Wallet wallet, LimitConfiguration limits, KycCompletedEvent event) {
        // Update daily limit
        wallet.setDailyLimit(limits.dailyLimit);
        
        // Update monthly limit
        wallet.setMonthlyLimit(limits.monthlyLimit);
        
        // Update single transaction limit (stored in metadata)
        wallet.getMetadata().put("singleTransactionLimit", limits.singleTransactionLimit.toString());
        
        // Update max balance (stored in metadata)
        wallet.getMetadata().put("maxBalance", limits.maxBalance.toString());
        
        // Store KYC verification details
        wallet.getMetadata().put("kycVerificationId", event.getKycVerificationId());
        wallet.getMetadata().put("kycVerificationLevel", event.getVerificationLevel().toString());
        wallet.getMetadata().put("kycVerificationDate", event.getVerificationCompletedAt().toString());
        wallet.getMetadata().put("kycAccountTier", event.getAccountTier().toString());
        wallet.getMetadata().put("kycRiskLevel", event.getRiskLevel().toString());
        
        log.info("Updated wallet {} limits - Daily: {}, Monthly: {}, Single: {}",
            wallet.getId(), limits.dailyLimit, limits.monthlyLimit, limits.singleTransactionLimit);
    }
    
    /**
     * Update wallet features based on KYC verification
     */
    private void updateWalletFeatures(Wallet wallet, KycCompletedEvent event) {
        Map<String, Boolean> features = new HashMap<>();
        
        // Basic features for all verified users
        features.put("transfersEnabled", true);
        features.put("paymentsEnabled", true);
        features.put("withdrawalsEnabled", true);
        
        // Advanced features based on verification level
        switch (event.getVerificationLevel()) {
            case PREMIUM:
            case ENHANCED:
                features.put("cryptoEnabled", true);
                features.put("forexEnabled", true);
                features.put("investmentsEnabled", true);
                // Fall through
            case STANDARD:
                features.put("internationalTransfersEnabled", true);
                features.put("virtualCardsEnabled", true);
                features.put("savingsGoalsEnabled", true);
                // Fall through
            case BASIC:
                features.put("p2pTransfersEnabled", true);
                features.put("billPaymentsEnabled", true);
                break;
        }
        
        // Apply restrictions based on risk and compliance
        if (event.isHighRisk()) {
            features.put("internationalTransfersEnabled", false);
            features.put("cryptoEnabled", false);
            features.put("largeTransfersEnabled", false);
        }
        
        if (event.hasComplianceIssues()) {
            features.put("withdrawalsEnabled", false);
            features.put("internationalTransfersEnabled", false);
        }
        
        // Handle specific enabled/restricted features from KYC
        if (event.getEnabledFeatures() != null) {
            for (String feature : event.getEnabledFeatures()) {
                features.put(feature, true);
            }
        }
        
        if (event.getRestrictedFeatures() != null) {
            for (String feature : event.getRestrictedFeatures()) {
                features.put(feature, false);
            }
        }
        
        // Store features in wallet metadata
        wallet.getMetadata().put("features", features);
        
        log.info("Updated wallet {} features: {}", wallet.getId(), features);
    }
    
    /**
     * Update wallet status based on KYC verification
     */
    private void updateWalletStatus(Wallet wallet, KycCompletedEvent event) {
        // Activate wallet if KYC is successful and account should be activated
        if (event.canActivateImmediately() && wallet.getStatus() != WalletStatus.ACTIVE) {
            wallet.setStatus(WalletStatus.ACTIVE);
            log.info("Activated wallet {} after KYC completion", wallet.getId());
        }
        
        // Handle conditional activation
        if (event.getVerificationStatus() == KycCompletedEvent.VerificationStatus.COMPLETED_WITH_CONDITIONS) {
            wallet.getMetadata().put("activationConditions", event.getAdditionalVerificationRequired());
            log.info("Wallet {} activated with conditions: {}", 
                wallet.getId(), event.getAdditionalVerificationRequired());
        }
        
        // Handle manual review requirements
        if (event.requiresManualIntervention()) {
            wallet.getMetadata().put("requiresManualReview", true);
            wallet.getMetadata().put("manualReviewReason", "KYC verification requires manual review");
            log.warn("Wallet {} requires manual review after KYC", wallet.getId());
        }
    }
    
    /**
     * Store KYC status for users without wallets
     */
    private void storeKycStatusForFutureWallet(KycCompletedEvent event) {
        Map<String, Object> kycStatus = new HashMap<>();
        kycStatus.put("userId", event.getUserId());
        kycStatus.put("kycVerificationId", event.getKycVerificationId());
        kycStatus.put("verificationLevel", event.getVerificationLevel());
        kycStatus.put("accountTier", event.getAccountTier());
        kycStatus.put("verificationDate", event.getVerificationCompletedAt());
        kycStatus.put("limits", TIER_LIMITS.get(event.getAccountTier()));
        
        // Store in cache or database for future wallet creation
        kafkaTemplate.send("kyc-status-cache", event.getUserId(), kycStatus);
        
        log.info("Stored KYC status for future wallet creation for user: {}", event.getUserId());
    }
    
    /**
     * Send notifications about KYC completion and limit updates
     */
    private void sendKycCompletionNotifications(Wallet wallet, KycCompletedEvent event) {
        // Notify user about successful KYC and new limits
        if (Boolean.TRUE.equals(event.getSendWelcomeEmail())) {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("userId", event.getUserId());
            notificationData.put("verificationLevel", event.getVerificationLevel());
            notificationData.put("accountTier", event.getAccountTier());
            notificationData.put("dailyLimit", wallet.getDailyLimit());
            notificationData.put("monthlyLimit", wallet.getMonthlyLimit());
            notificationData.put("features", wallet.getMetadata().get("features"));
            
            notificationService.sendKycCompletionNotification(
                wallet.getUserId(),
                "KYC Verification Completed",
                generateKycCompletionMessage(event, wallet),
                notificationData
            );
        }
        
        // Notify about any restrictions or conditions
        if (event.isHighRisk() || event.hasComplianceIssues()) {
            notifyAboutRestrictions(wallet, event);
        }
    }
    
    /**
     * Generate KYC completion message for user
     */
    private String generateKycCompletionMessage(KycCompletedEvent event, Wallet wallet) {
        StringBuilder message = new StringBuilder();
        message.append("Congratulations! Your identity verification is complete. ");
        message.append("Your account has been upgraded to ").append(event.getAccountTier().getDescription()).append(". ");
        message.append("You can now enjoy the following benefits:\n");
        message.append("• Daily transaction limit: $").append(wallet.getDailyLimit()).append("\n");
        message.append("• Monthly transaction limit: $").append(wallet.getMonthlyLimit()).append("\n");
        
        @SuppressWarnings("unchecked")
        Map<String, Boolean> features = (Map<String, Boolean>) wallet.getMetadata().get("features");
        if (features != null && features.get("internationalTransfersEnabled") == Boolean.TRUE) {
            message.append("• International transfers enabled\n");
        }
        if (features != null && features.get("cryptoEnabled") == Boolean.TRUE) {
            message.append("• Cryptocurrency trading enabled\n");
        }
        
        if (event.getRequireAdditionalVerification() == Boolean.TRUE) {
            message.append("\nNote: Some features may require additional verification.");
        }
        
        return message.toString();
    }
    
    /**
     * Notify user about restrictions
     */
    private void notifyAboutRestrictions(Wallet wallet, KycCompletedEvent event) {
        Map<String, Object> restrictionData = new HashMap<>();
        restrictionData.put("userId", event.getUserId());
        restrictionData.put("riskLevel", event.getRiskLevel());
        restrictionData.put("complianceFlags", event.getComplianceFlags());
        restrictionData.put("restrictions", event.getRestrictedFeatures());
        
        String message = "Your account has been verified with some restrictions due to ";
        if (event.isHighRisk()) {
            message += "elevated risk factors. ";
        }
        if (event.hasComplianceIssues()) {
            message += "compliance requirements. ";
        }
        message += "Please contact support for more information.";
        
        notificationService.sendRestrictionNotification(
            wallet.getUserId(),
            "Account Restrictions Applied",
            message,
            restrictionData
        );
    }
    
    /**
     * Publish wallet limit update events
     */
    private void publishWalletLimitUpdateEvents(List<Wallet> wallets, KycCompletedEvent event) {
        for (Wallet wallet : wallets) {
            Map<String, Object> updateEvent = new HashMap<>();
            updateEvent.put("eventId", UUID.randomUUID().toString());
            updateEvent.put("timestamp", LocalDateTime.now());
            updateEvent.put("walletId", wallet.getId());
            updateEvent.put("userId", wallet.getUserId());
            updateEvent.put("kycVerificationId", event.getKycVerificationId());
            updateEvent.put("oldLimits", Map.of(
                "daily", wallet.getDailyLimit(),
                "monthly", wallet.getMonthlyLimit()
            ));
            updateEvent.put("newLimits", Map.of(
                "daily", wallet.getDailyLimit(),
                "monthly", wallet.getMonthlyLimit()
            ));
            updateEvent.put("accountTier", event.getAccountTier());
            updateEvent.put("service", "wallet-service");
            
            kafkaTemplate.send("wallet-limit-updates", updateEvent);
        }
        
        log.debug("Published wallet limit update events for user: {}", event.getUserId());
    }
    
    /**
     * Log wallet update for audit
     */
    private void logWalletUpdate(Wallet wallet, KycCompletedEvent event, LimitConfiguration limits) {
        Map<String, Object> auditLog = new HashMap<>();
        auditLog.put("walletId", wallet.getId());
        auditLog.put("userId", wallet.getUserId());
        auditLog.put("kycEventId", event.getEventId());
        auditLog.put("verificationLevel", event.getVerificationLevel());
        auditLog.put("accountTier", event.getAccountTier());
        auditLog.put("appliedLimits", limits);
        auditLog.put("riskLevel", event.getRiskLevel());
        auditLog.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("wallet-audit-logs", auditLog);
        
        log.info("Logged wallet update for audit: wallet={}, user={}, tier={}",
            wallet.getId(), wallet.getUserId(), event.getAccountTier());
    }
    
    /**
     * Check if event is duplicate
     */
    private boolean isDuplicateEvent(String eventId) {
        // Clean old entries (older than 1 hour)
        cleanOldProcessedEvents();
        
        return processedEvents.containsKey(eventId);
    }
    
    /**
     * Mark event as processed
     */
    private void markEventProcessed(String eventId) {
        processedEvents.put(eventId, LocalDateTime.now());
    }
    
    /**
     * Clean old processed events from memory
     */
    private void cleanOldProcessedEvents() {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        processedEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(oneHourAgo));
    }
    
    /**
     * Publish error event for monitoring
     */
    private void publishErrorEvent(KycCompletedEvent event, Exception error) {
        Map<String, Object> errorEvent = new HashMap<>();
        errorEvent.put("eventId", event.getEventId());
        errorEvent.put("userId", event.getUserId());
        errorEvent.put("error", error.getMessage());
        errorEvent.put("timestamp", LocalDateTime.now());
        errorEvent.put("service", "wallet-service");
        
        kafkaTemplate.send("kyc-processing-errors", errorEvent);
    }
    
    /**
     * Limit configuration class
     */
    private static class LimitConfiguration {
        final BigDecimal dailyLimit;
        final BigDecimal monthlyLimit;
        final BigDecimal singleTransactionLimit;
        final BigDecimal maxBalance;
        final boolean internationalTransfersEnabled;
        
        LimitConfiguration(BigDecimal dailyLimit, BigDecimal monthlyLimit, 
                          BigDecimal singleTransactionLimit, BigDecimal maxBalance,
                          boolean internationalTransfersEnabled) {
            this.dailyLimit = dailyLimit;
            this.monthlyLimit = monthlyLimit;
            this.singleTransactionLimit = singleTransactionLimit;
            this.maxBalance = maxBalance;
            this.internationalTransfersEnabled = internationalTransfersEnabled;
        }
    }
}
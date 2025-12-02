package com.waqiti.wallet.consumer;

import com.waqiti.common.events.WalletLimitExceededEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.idempotency.RedisIdempotencyService;
import com.waqiti.wallet.service.WalletLimitService;
import com.waqiti.wallet.service.TransactionRestrictionService;
import com.waqiti.wallet.service.ComplianceCheckService;
import com.waqiti.wallet.service.NotificationService;
import com.waqiti.wallet.repository.ProcessedEventRepository;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.model.ProcessedEvent;
import com.waqiti.wallet.model.Wallet;
import com.waqiti.wallet.model.LimitType;
import com.waqiti.wallet.model.RestrictionLevel;
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
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for WalletLimitExceededEvent - Critical for transaction limit enforcement
 * Handles wallet restrictions, compliance checks, and user notifications
 * ZERO TOLERANCE: All limit breaches must trigger appropriate restrictions
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WalletLimitExceededEventConsumer {

    private final WalletLimitService walletLimitService;
    private final TransactionRestrictionService transactionRestrictionService;
    private final ComplianceCheckService complianceCheckService;
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final WalletRepository walletRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UniversalDLQHandler dlqHandler;
    private final RedisIdempotencyService idempotencyService;

    private static final BigDecimal SUSPICIOUS_MULTIPLIER = new BigDecimal("2.0");
    
    @KafkaListener(
        topics = "wallet.limit.exceeded",
        groupId = "wallet-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for limit enforcement
    public void handleWalletLimitExceeded(
            @Payload WalletLimitExceededEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        // Build idempotency key
        String idempotencyKey = idempotencyService.buildIdempotencyKey(
            "wallet-service",
            "WalletLimitExceededEvent",
            event.getEventId()
        );

        log.warn("Wallet limit exceeded: Wallet {} exceeded {} limit - Attempted: ${}, Limit: ${}",
            event.getWalletId(), event.getLimitType(), event.getAttemptedAmount(), event.getLimitAmount());

        // Universal idempotency check - Prevent duplicate limit enforcement (30-day TTL)
        if (idempotencyService.isProcessed(idempotencyKey)) {
            log.info("⏭️ Limit exceeded event already processed: {}", event.getEventId());
            acknowledgment.acknowledge();
            return;
        }
        
        try {
            // Get wallet from database
            Wallet wallet = walletRepository.findById(event.getWalletId())
                .orElseThrow(() -> new RuntimeException("Wallet not found: " + event.getWalletId()));
            
            // STEP 1: Apply immediate transaction restrictions
            applyTransactionRestrictions(wallet, event);
            
            // STEP 2: Check for suspicious patterns
            boolean isSuspicious = checkSuspiciousActivity(wallet, event);
            
            // STEP 3: Perform enhanced compliance checks
            performComplianceChecks(wallet, event, isSuspicious);
            
            // STEP 4: Update wallet limits based on violation
            adjustWalletLimits(wallet, event);
            
            // STEP 5: Create limit breach record for audit
            createLimitBreachRecord(wallet, event);
            
            // STEP 6: Send user notifications
            sendLimitExceededNotifications(wallet, event);
            
            // STEP 7: Check if KYC upgrade needed
            if (shouldPromptKYCUpgrade(wallet, event)) {
                initiateKYCUpgradeFlow(wallet, event);
            }
            
            // STEP 8: Report to risk management system
            reportToRiskManagement(wallet, event, isSuspicious);
            
            // STEP 9: Apply cooling-off period if needed
            if (requiresCoolingOff(wallet, event)) {
                applyCoolingOffPeriod(wallet, event);
            }
            
            // STEP 10: Update wallet statistics
            updateWalletStatistics(wallet, event);
            
            // STEP 11: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("WalletLimitExceededEvent")
                .processedAt(Instant.now())
                .walletId(event.getWalletId())
                .userId(event.getUserId())
                .limitType(event.getLimitType().toString())
                .attemptedAmount(event.getAttemptedAmount())
                .restrictionsApplied(true)
                .build();

            processedEventRepository.save(processedEvent);

            // Mark as processed (30-day TTL for financial operations)
            idempotencyService.markFinancialOperationProcessed(idempotencyKey);

            log.info("Successfully processed limit exceeded event for wallet: {} - Restrictions applied",
                event.getWalletId());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("CRITICAL: Failed to process wallet limit exceeded: walletId={}, topic={}, partition={}, offset={}, error={}",
                event.getWalletId(), topic, partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(event, topic, partition, offset, e)
                .thenAccept(result -> log.info("Wallet limit exceeded event sent to DLQ: walletId={}, destination={}, category={}",
                        event.getWalletId(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for wallet limit exceeded - MESSAGE MAY BE LOST! " +
                            "walletId={}, partition={}, offset={}, error={}",
                            event.getWalletId(), partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            // Apply emergency restrictions as fallback
            applyEmergencyRestrictions(event, e);

            throw new RuntimeException("Wallet limit exceeded processing failed", e);
        }
    }
    
    private void applyTransactionRestrictions(Wallet wallet, WalletLimitExceededEvent event) {
        RestrictionLevel restrictionLevel = determineRestrictionLevel(event);
        
        switch (restrictionLevel) {
            case TEMPORARY_HOLD -> {
                // Apply temporary hold for minor violations
                transactionRestrictionService.applyTemporaryHold(
                    wallet.getId(),
                    event.getLimitType(),
                    LocalDateTime.now().plusHours(1),
                    "Temporary hold due to limit breach"
                );
                wallet.setTemporarilyRestricted(true);
            }
            case PARTIAL_RESTRICTION -> {
                // Restrict certain transaction types
                transactionRestrictionService.restrictTransactionTypes(
                    wallet.getId(),
                    Arrays.asList("INTERNATIONAL", "CRYPTO", "HIGH_VALUE"),
                    "Partial restriction due to repeated limit breaches"
                );
                wallet.setPartiallyRestricted(true);
            }
            case FULL_RESTRICTION -> {
                // Full wallet freeze for severe violations
                transactionRestrictionService.freezeWallet(
                    wallet.getId(),
                    "LIMIT_VIOLATION",
                    "Full restriction due to severe limit breach"
                );
                wallet.setStatus("FROZEN");
                wallet.setFrozenAt(LocalDateTime.now());
                wallet.setFreezeReason("LIMIT_EXCEEDED");
            }
        }
        
        walletRepository.save(wallet);
        
        log.info("Applied {} restriction to wallet: {}", restrictionLevel, wallet.getId());
    }
    
    private boolean checkSuspiciousActivity(Wallet wallet, WalletLimitExceededEvent event) {
        // Check for suspicious patterns
        boolean multipleLimitBreaches = walletLimitService.hasMultipleLimitBreaches(
            wallet.getId(),
            LocalDateTime.now().minusHours(24)
        );
        
        boolean unusualAmount = event.getAttemptedAmount()
            .compareTo(event.getLimitAmount().multiply(SUSPICIOUS_MULTIPLIER)) > 0;
        
        boolean rapidTransactions = walletLimitService.hasRapidTransactions(
            wallet.getId(),
            LocalDateTime.now().minusMinutes(10),
            5 // More than 5 transactions in 10 minutes
        );
        
        boolean differentGeolocations = walletLimitService.hasMultipleGeolocations(
            wallet.getId(),
            LocalDateTime.now().minusHours(1)
        );
        
        boolean isSuspicious = multipleLimitBreaches || unusualAmount || 
                               rapidTransactions || differentGeolocations;
        
        if (isSuspicious) {
            log.warn("Suspicious activity detected for wallet: {} - Patterns: Breaches={}, Unusual={}, Rapid={}, Geo={}", 
                wallet.getId(), multipleLimitBreaches, unusualAmount, rapidTransactions, differentGeolocations);
            
            // Flag wallet for enhanced monitoring
            wallet.setEnhancedMonitoring(true);
            wallet.setSuspiciousActivityScore(calculateSuspiciousScore(
                multipleLimitBreaches, unusualAmount, rapidTransactions, differentGeolocations
            ));
        }
        
        return isSuspicious;
    }
    
    private void performComplianceChecks(Wallet wallet, WalletLimitExceededEvent event, boolean isSuspicious) {
        // Run enhanced compliance checks
        String complianceCheckId = complianceCheckService.initiateEnhancedCheck(
            wallet.getId(),
            event.getUserId(),
            event.getAttemptedAmount(),
            event.getTransactionType(),
            isSuspicious
        );
        
        wallet.setLastComplianceCheckId(complianceCheckId);
        wallet.setLastComplianceCheckAt(LocalDateTime.now());
        
        // Check against regulatory thresholds
        if (event.getAttemptedAmount().compareTo(new BigDecimal("10000")) > 0) {
            // CTR (Currency Transaction Report) threshold
            complianceCheckService.createCTRReport(
                wallet.getId(),
                event.getUserId(),
                event.getAttemptedAmount(),
                event.getTransactionType()
            );
        }
        
        if (isSuspicious && event.getAttemptedAmount().compareTo(new BigDecimal("5000")) > 0) {
            // SAR (Suspicious Activity Report) threshold
            complianceCheckService.createSARReport(
                wallet.getId(),
                event.getUserId(),
                event.getAttemptedAmount(),
                "Limit breach with suspicious patterns"
            );
        }
        
        walletRepository.save(wallet);
        
        log.info("Compliance checks completed for wallet: {} - Check ID: {}", 
            wallet.getId(), complianceCheckId);
    }
    
    private void adjustWalletLimits(Wallet wallet, WalletLimitExceededEvent event) {
        // Adjust limits based on violation history
        int violationCount = walletLimitService.getViolationCount(
            wallet.getId(),
            LocalDateTime.now().minusDays(30)
        );
        
        if (violationCount >= 3) {
            // Reduce limits for repeat violators
            BigDecimal reductionFactor = new BigDecimal("0.5");
            
            walletLimitService.adjustLimit(
                wallet.getId(),
                LimitType.DAILY,
                wallet.getDailyLimit().multiply(reductionFactor)
            );
            
            walletLimitService.adjustLimit(
                wallet.getId(),
                LimitType.MONTHLY,
                wallet.getMonthlyLimit().multiply(reductionFactor)
            );
            
            wallet.setLimitsReduced(true);
            wallet.setLimitsReducedAt(LocalDateTime.now());
            wallet.setLimitsReductionReason("REPEATED_VIOLATIONS");
            
            log.info("Reduced limits for wallet {} due to {} violations", 
                wallet.getId(), violationCount);
        }
        
        walletRepository.save(wallet);
    }
    
    private void createLimitBreachRecord(Wallet wallet, WalletLimitExceededEvent event) {
        // Create detailed audit record
        walletLimitService.recordLimitBreach(
            wallet.getId(),
            event.getUserId(),
            event.getLimitType(),
            event.getLimitAmount(),
            event.getAttemptedAmount(),
            event.getTransactionType(),
            event.getTransactionId(),
            event.getTimestamp()
        );
        
        // Update wallet breach statistics
        wallet.incrementLimitBreachCount();
        wallet.setLastLimitBreachAt(LocalDateTime.now());
        wallet.setLastLimitBreachType(event.getLimitType().toString());
        
        walletRepository.save(wallet);
        
        log.info("Limit breach record created for wallet: {}", wallet.getId());
    }
    
    private void sendLimitExceededNotifications(Wallet wallet, WalletLimitExceededEvent event) {
        // Send immediate push notification
        notificationService.sendLimitExceededPush(
            event.getUserId(),
            wallet.getId(),
            event.getLimitType(),
            event.getAttemptedAmount(),
            event.getLimitAmount()
        );
        
        // Send detailed email
        notificationService.sendLimitExceededEmail(
            event.getUserId(),
            wallet,
            event,
            determineRestrictionLevel(event)
        );
        
        // SMS for high-value attempts
        if (event.getAttemptedAmount().compareTo(new BigDecimal("1000")) > 0) {
            notificationService.sendLimitExceededSMS(
                event.getUserId(),
                String.format("Your %s limit of $%.2f was exceeded. Transaction blocked. Contact support if needed.",
                    event.getLimitType().toString().toLowerCase(),
                    event.getLimitAmount())
            );
        }
        
        log.info("Limit exceeded notifications sent for wallet: {}", wallet.getId());
    }
    
    private boolean shouldPromptKYCUpgrade(Wallet wallet, WalletLimitExceededEvent event) {
        // Check if user should upgrade KYC for higher limits
        return wallet.getKycLevel() < 3 && 
               event.getLimitType() == LimitType.TRANSACTION &&
               walletLimitService.hasConsistentLimitUsage(wallet.getId(), 0.8); // Using 80% of limits
    }
    
    private void initiateKYCUpgradeFlow(Wallet wallet, WalletLimitExceededEvent event) {
        // Initiate KYC upgrade process
        Map<String, Object> upgradeEvent = Map.of(
            "eventId", UUID.randomUUID().toString(),
            "userId", event.getUserId(),
            "walletId", wallet.getId(),
            "currentKycLevel", wallet.getKycLevel(),
            "requestedLevel", wallet.getKycLevel() + 1,
            "reason", "LIMIT_UPGRADE_REQUEST",
            "triggerEvent", event.getEventId()
        );
        
        kafkaTemplate.send("kyc.upgrade.requested", upgradeEvent);
        
        // Send upgrade prompt to user
        notificationService.sendKYCUpgradePrompt(
            event.getUserId(),
            wallet.getId(),
            calculateNewLimitsForKYCLevel(wallet.getKycLevel() + 1)
        );
        
        log.info("KYC upgrade flow initiated for wallet: {}", wallet.getId());
    }
    
    private void reportToRiskManagement(Wallet wallet, WalletLimitExceededEvent event, boolean isSuspicious) {
        // Report to risk management system
        Map<String, Object> riskEvent = Map.of(
            "eventId", UUID.randomUUID().toString(),
            "walletId", wallet.getId(),
            "userId", event.getUserId(),
            "limitType", event.getLimitType().toString(),
            "attemptedAmount", event.getAttemptedAmount(),
            "limitAmount", event.getLimitAmount(),
            "isSuspicious", isSuspicious,
            "violationCount", walletLimitService.getViolationCount(wallet.getId(), LocalDateTime.now().minusDays(30)),
            "riskScore", wallet.getSuspiciousActivityScore() != null ? wallet.getSuspiciousActivityScore() : 0
        );
        
        kafkaTemplate.send("risk.limit.violation", riskEvent);
        
        log.info("Risk event reported for wallet: {}", wallet.getId());
    }
    
    private boolean requiresCoolingOff(Wallet wallet, WalletLimitExceededEvent event) {
        // Determine if cooling-off period is needed
        return event.getLimitType() == LimitType.TRANSACTION &&
               walletLimitService.hasMultipleLimitBreaches(wallet.getId(), LocalDateTime.now().minusHours(1));
    }
    
    private void applyCoolingOffPeriod(Wallet wallet, WalletLimitExceededEvent event) {
        // Apply cooling-off period
        LocalDateTime coolingOffEnd = LocalDateTime.now().plusHours(
            event.getSeverity() == "HIGH" ? 24 : 6
        );
        
        transactionRestrictionService.applyCoolingOffPeriod(
            wallet.getId(),
            coolingOffEnd,
            "Cooling-off period due to repeated limit breaches"
        );
        
        wallet.setCoolingOffActive(true);
        wallet.setCoolingOffEndTime(coolingOffEnd);
        
        walletRepository.save(wallet);
        
        log.info("Cooling-off period applied to wallet {} until {}", 
            wallet.getId(), coolingOffEnd);
    }
    
    private void updateWalletStatistics(Wallet wallet, WalletLimitExceededEvent event) {
        // Update wallet usage statistics
        walletLimitService.updateUsageStatistics(
            wallet.getId(),
            event.getLimitType(),
            event.getAttemptedAmount(),
            false // Transaction was blocked
        );
        
        // Update risk metrics
        wallet.updateRiskMetrics(
            calculateNewRiskScore(wallet, event),
            LocalDateTime.now()
        );
        
        walletRepository.save(wallet);
        
        log.info("Wallet statistics updated for: {}", wallet.getId());
    }
    
    private RestrictionLevel determineRestrictionLevel(WalletLimitExceededEvent event) {
        // Determine appropriate restriction level
        BigDecimal excessPercentage = event.getAttemptedAmount()
            .subtract(event.getLimitAmount())
            .divide(event.getLimitAmount(), 2, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        if (excessPercentage.compareTo(new BigDecimal("100")) > 0) {
            return RestrictionLevel.FULL_RESTRICTION;
        } else if (excessPercentage.compareTo(new BigDecimal("50")) > 0) {
            return RestrictionLevel.PARTIAL_RESTRICTION;
        } else {
            return RestrictionLevel.TEMPORARY_HOLD;
        }
    }
    
    private double calculateSuspiciousScore(boolean breaches, boolean unusual, 
                                           boolean rapid, boolean geo) {
        double score = 0;
        if (breaches) score += 25;
        if (unusual) score += 30;
        if (rapid) score += 25;
        if (geo) score += 20;
        return score;
    }
    
    private Map<String, BigDecimal> calculateNewLimitsForKYCLevel(int kycLevel) {
        return Map.of(
            "daily", new BigDecimal(kycLevel * 5000),
            "monthly", new BigDecimal(kycLevel * 50000),
            "transaction", new BigDecimal(kycLevel * 2000)
        );
    }
    
    private double calculateNewRiskScore(Wallet wallet, WalletLimitExceededEvent event) {
        double baseScore = wallet.getRiskScore() != null ? wallet.getRiskScore() : 50.0;
        double breachImpact = 10.0 * (event.getAttemptedAmount().doubleValue() / event.getLimitAmount().doubleValue());
        return Math.min(100.0, baseScore + breachImpact);
    }
    
    private void applyEmergencyRestrictions(WalletLimitExceededEvent event, Exception originalException) {
        try {
            log.error("EMERGENCY: Applying emergency restrictions for wallet: {}", event.getWalletId());
            
            // Try to freeze wallet immediately
            transactionRestrictionService.emergencyFreeze(
                event.getWalletId(),
                "EMERGENCY_LIMIT_ENFORCEMENT_FAILURE"
            );
            
            // Send emergency alert
            notificationService.sendEmergencyAlert(
                event.getUserId(),
                event.getWalletId(),
                "Your wallet has been temporarily restricted due to a limit violation. Please contact support."
            );
            
        } catch (Exception e) {
            log.error("Failed to apply emergency restrictions", e);
        }
    }
}
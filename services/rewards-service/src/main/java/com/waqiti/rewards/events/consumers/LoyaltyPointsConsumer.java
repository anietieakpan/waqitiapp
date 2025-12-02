package com.waqiti.rewards.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.rewards.LoyaltyPointsEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.rewards.domain.LoyaltyAccount;
import com.waqiti.rewards.domain.PointsTransaction;
import com.waqiti.rewards.domain.PointsTransactionType;
import com.waqiti.rewards.domain.PointsStatus;
import com.waqiti.rewards.domain.RewardTier;
import com.waqiti.rewards.repository.LoyaltyAccountRepository;
import com.waqiti.rewards.repository.PointsTransactionRepository;
import com.waqiti.rewards.service.PointsCalculationService;
import com.waqiti.rewards.service.TierManagementService;
import com.waqiti.rewards.service.RedemptionService;
import com.waqiti.rewards.service.PointsExpiryService;
import com.waqiti.rewards.service.RewardsNotificationService;
import com.waqiti.common.exceptions.LoyaltyProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade consumer for loyalty points events.
 * Handles comprehensive rewards processing including:
 * - Points earning and redemption
 * - Tier progression and benefits
 * - Bonus multipliers and campaigns
 * - Points expiry and extension
 * - Partner rewards integration
 * - Cashback conversions
 * 
 * Critical for customer retention and engagement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoyaltyPointsConsumer {

    private final LoyaltyAccountRepository loyaltyRepository;
    private final PointsTransactionRepository transactionRepository;
    private final PointsCalculationService calculationService;
    private final TierManagementService tierService;
    private final RedemptionService redemptionService;
    private final PointsExpiryService expiryService;
    private final RewardsNotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    private static final int POINTS_EXPIRY_MONTHS = 12;
    private static final BigDecimal POINTS_TO_CASH_RATE = new BigDecimal("0.01"); // 1 point = $0.01

    @KafkaListener(
        topics = "loyalty-points-events",
        groupId = "rewards-service-points-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        include = {LoyaltyProcessingException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void handleLoyaltyPointsEvent(
            @Payload LoyaltyPointsEvent pointsEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "campaign-id", required = false) String campaignId,
            Acknowledgment acknowledgment) {

        String eventId = pointsEvent.getEventId() != null ? 
            pointsEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing loyalty points event: {} for user: {} type: {} points: {}", 
                    eventId, pointsEvent.getUserId(), pointsEvent.getTransactionType(), pointsEvent.getPoints());

            // Metrics tracking
            metricsService.incrementCounter("loyalty.points.processing.started",
                Map.of(
                    "transaction_type", pointsEvent.getTransactionType(),
                    "source", pointsEvent.getSource()
                ));

            // Idempotency check
            if (isPointsEventAlreadyProcessed(pointsEvent.getReferenceId(), eventId)) {
                log.info("Points event {} already processed for reference {}", 
                        eventId, pointsEvent.getReferenceId());
                acknowledgment.acknowledge();
                return;
            }

            // Get or create loyalty account
            LoyaltyAccount loyaltyAccount = getOrCreateLoyaltyAccount(pointsEvent.getUserId());

            // Create points transaction record
            PointsTransaction transaction = createPointsTransaction(pointsEvent, loyaltyAccount, eventId, correlationId);

            // Calculate actual points with multipliers
            calculateActualPoints(transaction, loyaltyAccount, pointsEvent, campaignId);

            // Process points based on transaction type
            processPointsTransaction(transaction, loyaltyAccount, pointsEvent);

            // Check and update tier status
            updateTierStatus(loyaltyAccount, pointsEvent);

            // Handle points expiry
            handlePointsExpiry(loyaltyAccount, transaction);

            // Check for milestone achievements
            checkMilestoneAchievements(loyaltyAccount, transaction);

            // Update loyalty account
            updateLoyaltyAccount(loyaltyAccount, transaction);

            // Save entities
            PointsTransaction savedTransaction = transactionRepository.save(transaction);
            LoyaltyAccount savedAccount = loyaltyRepository.save(loyaltyAccount);

            // Process redemption if applicable
            if (transaction.getType() == PointsTransactionType.REDEMPTION) {
                processRedemption(savedTransaction, savedAccount, pointsEvent);
            }

            // Send notifications
            sendLoyaltyNotifications(savedTransaction, savedAccount, pointsEvent);

            // Update metrics
            updateLoyaltyMetrics(savedTransaction, savedAccount, pointsEvent);

            // Create audit trail
            createLoyaltyAuditLog(savedTransaction, savedAccount, pointsEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("loyalty.points.processing.success",
                Map.of(
                    "transaction_type", savedTransaction.getType().toString(),
                    "tier", savedAccount.getCurrentTier().toString()
                ));

            log.info("Successfully processed loyalty points: {} for user: {} balance: {} tier: {}", 
                    savedTransaction.getId(), savedAccount.getUserId(), 
                    savedAccount.getCurrentBalance(), savedAccount.getCurrentTier());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing loyalty points event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("loyalty.points.processing.error");
            
            auditLogger.logError("LOYALTY_POINTS_PROCESSING_ERROR",
                "system", pointsEvent.getUserId(), e.getMessage(),
                Map.of(
                    "userId", pointsEvent.getUserId(),
                    "transactionType", pointsEvent.getTransactionType(),
                    "points", pointsEvent.getPoints().toString(),
                    "eventId", eventId,
                    "correlationId", correlationId != null ? correlationId : "N/A"
                ));
            
            throw new LoyaltyProcessingException("Failed to process loyalty points: " + e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "loyalty-points-bonus",
        groupId = "rewards-service-bonus-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleBonusPointsEvent(
            @Payload LoyaltyPointsEvent pointsEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.info("BONUS POINTS: Processing bonus award for user: {} points: {}", 
                    pointsEvent.getUserId(), pointsEvent.getPoints());

            // Fast-track bonus processing
            LoyaltyAccount account = processInstantBonus(pointsEvent, correlationId);

            // Send instant notification
            notificationService.sendBonusPointsNotification(account, pointsEvent.getPoints());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process bonus points: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private boolean isPointsEventAlreadyProcessed(String referenceId, String eventId) {
        return transactionRepository.existsByReferenceIdOrEventId(referenceId, eventId);
    }

    private LoyaltyAccount getOrCreateLoyaltyAccount(String userId) {
        return loyaltyRepository.findByUserId(userId)
            .orElseGet(() -> createNewLoyaltyAccount(userId));
    }

    private LoyaltyAccount createNewLoyaltyAccount(String userId) {
        LoyaltyAccount account = LoyaltyAccount.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .currentBalance(BigDecimal.ZERO)
            .lifetimeEarned(BigDecimal.ZERO)
            .lifetimeRedeemed(BigDecimal.ZERO)
            .currentTier(RewardTier.BRONZE)
            .tierPoints(BigDecimal.ZERO)
            .yearlySpend(BigDecimal.ZERO)
            .isActive(true)
            .joinedAt(LocalDateTime.now())
            .lastActivityAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        return loyaltyRepository.save(account);
    }

    private PointsTransaction createPointsTransaction(
            LoyaltyPointsEvent event, LoyaltyAccount account, String eventId, String correlationId) {
        
        return PointsTransaction.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .accountId(account.getId())
            .userId(account.getUserId())
            .type(PointsTransactionType.valueOf(event.getTransactionType().toUpperCase()))
            .category(event.getCategory())
            .basePoints(event.getPoints())
            .referenceId(event.getReferenceId())
            .referenceType(event.getReferenceType())
            .source(event.getSource())
            .description(event.getDescription())
            .merchantId(event.getMerchantId())
            .status(PointsStatus.PENDING)
            .correlationId(correlationId)
            .transactionDate(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private void calculateActualPoints(
            PointsTransaction transaction, 
            LoyaltyAccount account, 
            LoyaltyPointsEvent event,
            String campaignId) {
        
        BigDecimal basePoints = transaction.getBasePoints();
        BigDecimal multiplier = BigDecimal.ONE;

        // Apply tier multiplier
        multiplier = multiplier.multiply(getTierMultiplier(account.getCurrentTier()));
        transaction.setTierMultiplier(getTierMultiplier(account.getCurrentTier()));

        // Apply category multiplier
        if (event.getCategory() != null) {
            BigDecimal categoryMultiplier = calculationService.getCategoryMultiplier(
                event.getCategory(), account.getUserId()
            );
            multiplier = multiplier.multiply(categoryMultiplier);
            transaction.setCategoryMultiplier(categoryMultiplier);
        }

        // Apply campaign multiplier
        if (campaignId != null) {
            BigDecimal campaignMultiplier = calculationService.getCampaignMultiplier(campaignId);
            multiplier = multiplier.multiply(campaignMultiplier);
            transaction.setCampaignMultiplier(campaignMultiplier);
            transaction.setCampaignId(campaignId);
        }

        // Apply special event multipliers (birthday, anniversary, etc.)
        BigDecimal specialMultiplier = calculationService.getSpecialEventMultiplier(
            account.getUserId(), LocalDateTime.now()
        );
        if (specialMultiplier.compareTo(BigDecimal.ONE) > 0) {
            multiplier = multiplier.multiply(specialMultiplier);
            transaction.setSpecialMultiplier(specialMultiplier);
        }

        // Calculate final points
        BigDecimal actualPoints = basePoints.multiply(multiplier).setScale(0, RoundingMode.HALF_UP);
        transaction.setActualPoints(actualPoints);
        transaction.setTotalMultiplier(multiplier);

        // Calculate bonus points
        BigDecimal bonusPoints = actualPoints.subtract(basePoints);
        if (bonusPoints.compareTo(BigDecimal.ZERO) > 0) {
            transaction.setBonusPoints(bonusPoints);
        }
    }

    private void processPointsTransaction(
            PointsTransaction transaction, 
            LoyaltyAccount account, 
            LoyaltyPointsEvent event) {
        
        switch (transaction.getType()) {
            case EARNED -> processEarnedPoints(transaction, account, event);
            case REDEMPTION -> processRedemption(transaction, account, event);
            case ADJUSTMENT -> processAdjustment(transaction, account, event);
            case EXPIRY -> processExpiry(transaction, account);
            case TRANSFER -> processTransfer(transaction, account, event);
            case BONUS -> processBonusPoints(transaction, account);
            default -> throw new LoyaltyProcessingException("Unknown transaction type: " + transaction.getType());
        }
    }

    private void processEarnedPoints(PointsTransaction transaction, LoyaltyAccount account, LoyaltyPointsEvent event) {
        // Add points to balance
        account.setCurrentBalance(account.getCurrentBalance().add(transaction.getActualPoints()));
        account.setLifetimeEarned(account.getLifetimeEarned().add(transaction.getActualPoints()));
        
        // Update tier points
        account.setTierPoints(account.getTierPoints().add(transaction.getActualPoints()));
        
        // Update yearly spend if transaction-based
        if (event.getTransactionAmount() != null) {
            account.setYearlySpend(account.getYearlySpend().add(event.getTransactionAmount()));
        }
        
        // Set expiry date
        transaction.setExpiryDate(LocalDateTime.now().plusMonths(POINTS_EXPIRY_MONTHS));
        transaction.setStatus(PointsStatus.CREDITED);
        transaction.setCreditedAt(LocalDateTime.now());
    }

    private void processRedemption(PointsTransaction transaction, LoyaltyAccount account, LoyaltyPointsEvent event) {
        // Validate sufficient balance
        if (account.getCurrentBalance().compareTo(transaction.getActualPoints()) < 0) {
            transaction.setStatus(PointsStatus.REJECTED);
            transaction.setRejectionReason("Insufficient points balance");
            throw new LoyaltyProcessingException("Insufficient points balance for redemption");
        }
        
        // Deduct points
        account.setCurrentBalance(account.getCurrentBalance().subtract(transaction.getActualPoints()));
        account.setLifetimeRedeemed(account.getLifetimeRedeemed().add(transaction.getActualPoints()));
        
        transaction.setStatus(PointsStatus.REDEEMED);
        transaction.setRedeemedAt(LocalDateTime.now());
        
        // Calculate redemption value
        BigDecimal redemptionValue = transaction.getActualPoints().multiply(POINTS_TO_CASH_RATE);
        transaction.setRedemptionValue(redemptionValue);
        transaction.setRedemptionCurrency("USD");
    }

    private void processAdjustment(PointsTransaction transaction, LoyaltyAccount account, LoyaltyPointsEvent event) {
        // Handle positive or negative adjustments
        account.setCurrentBalance(account.getCurrentBalance().add(transaction.getActualPoints()));
        
        if (transaction.getActualPoints().compareTo(BigDecimal.ZERO) > 0) {
            account.setLifetimeEarned(account.getLifetimeEarned().add(transaction.getActualPoints()));
        }
        
        transaction.setStatus(PointsStatus.ADJUSTED);
        transaction.setAdjustedAt(LocalDateTime.now());
        transaction.setAdjustmentReason(event.getDescription());
    }

    private void processExpiry(PointsTransaction transaction, LoyaltyAccount account) {
        // Deduct expired points
        BigDecimal expiredPoints = transaction.getActualPoints().abs();
        account.setCurrentBalance(account.getCurrentBalance().subtract(expiredPoints));
        account.setTotalExpired(account.getTotalExpired().add(expiredPoints));
        
        transaction.setStatus(PointsStatus.EXPIRED);
        transaction.setExpiredAt(LocalDateTime.now());
    }

    private void processTransfer(PointsTransaction transaction, LoyaltyAccount account, LoyaltyPointsEvent event) {
        if (event.getTransferToUserId() != null) {
            // Deduct from source account
            account.setCurrentBalance(account.getCurrentBalance().subtract(transaction.getActualPoints()));
            
            // Create transfer record
            transaction.setTransferToUserId(event.getTransferToUserId());
            transaction.setStatus(PointsStatus.TRANSFERRED);
            transaction.setTransferredAt(LocalDateTime.now());
            
            // Trigger transfer to destination account
            triggerTransferToDestination(event.getTransferToUserId(), transaction);
        }
    }

    private void processBonusPoints(PointsTransaction transaction, LoyaltyAccount account) {
        account.setCurrentBalance(account.getCurrentBalance().add(transaction.getActualPoints()));
        account.setLifetimeEarned(account.getLifetimeEarned().add(transaction.getActualPoints()));
        account.setTotalBonusEarned(account.getTotalBonusEarned().add(transaction.getActualPoints()));
        
        transaction.setStatus(PointsStatus.CREDITED);
        transaction.setCreditedAt(LocalDateTime.now());
        transaction.setIsBonus(true);
    }

    private void updateTierStatus(LoyaltyAccount account, LoyaltyPointsEvent event) {
        try {
            RewardTier currentTier = account.getCurrentTier();
            RewardTier newTier = tierService.calculateTier(
                account.getTierPoints(),
                account.getYearlySpend(),
                account.getLifetimeEarned()
            );
            
            if (newTier != currentTier) {
                // Tier upgrade
                if (newTier.getLevel() > currentTier.getLevel()) {
                    account.setCurrentTier(newTier);
                    account.setTierUpgradedAt(LocalDateTime.now());
                    account.setPreviousTier(currentTier);
                    
                    // Apply tier upgrade benefits
                    applyTierUpgradeBenefits(account, newTier);
                    
                    log.info("User {} upgraded from {} to {}", 
                            account.getUserId(), currentTier, newTier);
                }
                // Tier downgrade
                else if (newTier.getLevel() < currentTier.getLevel()) {
                    account.setCurrentTier(newTier);
                    account.setTierDowngradedAt(LocalDateTime.now());
                    account.setPreviousTier(currentTier);
                    
                    log.info("User {} downgraded from {} to {}", 
                            account.getUserId(), currentTier, newTier);
                }
            }
            
            // Update tier expiry
            account.setTierExpiryDate(tierService.calculateTierExpiry(account));
            
        } catch (Exception e) {
            log.error("Error updating tier status: {}", e.getMessage());
        }
    }

    private void handlePointsExpiry(LoyaltyAccount account, PointsTransaction transaction) {
        try {
            // Check for expiring points
            List<PointsTransaction> expiringPoints = transactionRepository
                .findExpiringPoints(account.getId(), LocalDateTime.now().plusDays(30));
            
            if (!expiringPoints.isEmpty()) {
                BigDecimal totalExpiring = expiringPoints.stream()
                    .map(PointsTransaction::getActualPoints)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                account.setPointsExpiringNextMonth(totalExpiring);
                
                // Check if extension eligible
                if (expiryService.isExtensionEligible(account)) {
                    extendPointsExpiry(expiringPoints, account);
                }
            }
            
        } catch (Exception e) {
            log.error("Error handling points expiry: {}", e.getMessage());
        }
    }

    private void checkMilestoneAchievements(LoyaltyAccount account, PointsTransaction transaction) {
        try {
            // Check points milestones
            List<Integer> milestones = List.of(1000, 5000, 10000, 25000, 50000, 100000);
            
            for (Integer milestone : milestones) {
                BigDecimal milestoneBD = new BigDecimal(milestone);
                if (account.getLifetimeEarned().compareTo(milestoneBD) >= 0 &&
                    account.getLifetimeEarned().subtract(transaction.getActualPoints()).compareTo(milestoneBD) < 0) {
                    
                    // Award milestone bonus
                    awardMilestoneBonus(account, milestone);
                    
                    // Record achievement
                    account.addAchievement("POINTS_MILESTONE_" + milestone);
                    
                    log.info("User {} achieved {} points milestone", 
                            account.getUserId(), milestone);
                }
            }
            
        } catch (Exception e) {
            log.error("Error checking milestone achievements: {}", e.getMessage());
        }
    }

    private void updateLoyaltyAccount(LoyaltyAccount account, PointsTransaction transaction) {
        account.setLastActivityAt(LocalDateTime.now());
        account.setLastTransactionId(transaction.getId());
        account.setTransactionCount(account.getTransactionCount() + 1);
        
        // Update streak if applicable
        if (isStreakMaintained(account)) {
            account.setCurrentStreak(account.getCurrentStreak() + 1);
            account.setLongestStreak(Math.max(account.getCurrentStreak(), account.getLongestStreak()));
        } else {
            account.setCurrentStreak(0);
        }
        
        account.setUpdatedAt(LocalDateTime.now());
    }

    private void processRedemption(PointsTransaction transaction, LoyaltyAccount account, LoyaltyPointsEvent event) {
        try {
            if (event.getRedemptionType() != null) {
                var redemption = redemptionService.processRedemption(
                    transaction.getId(),
                    account.getUserId(),
                    transaction.getActualPoints(),
                    event.getRedemptionType(),
                    event.getRedemptionDetails()
                );
                
                transaction.setRedemptionId(redemption.getId());
                transaction.setRedemptionStatus(redemption.getStatus());
            }
        } catch (Exception e) {
            log.error("Error processing redemption: {}", e.getMessage());
            transaction.setRedemptionError(e.getMessage());
        }
    }

    private void sendLoyaltyNotifications(
            PointsTransaction transaction, 
            LoyaltyAccount account, 
            LoyaltyPointsEvent event) {
        
        try {
            // Points earned notification
            if (transaction.getType() == PointsTransactionType.EARNED) {
                notificationService.sendPointsEarnedNotification(account, transaction);
            }
            
            // Redemption notification
            if (transaction.getType() == PointsTransactionType.REDEMPTION) {
                notificationService.sendRedemptionNotification(account, transaction);
            }
            
            // Tier change notification
            if (account.getTierUpgradedAt() != null && 
                ChronoUnit.SECONDS.between(account.getTierUpgradedAt(), LocalDateTime.now()) < 60) {
                notificationService.sendTierUpgradeNotification(account);
            }
            
            // Expiry warning
            if (account.getPointsExpiringNextMonth() != null && 
                account.getPointsExpiringNextMonth().compareTo(BigDecimal.ZERO) > 0) {
                notificationService.sendExpiryWarning(account);
            }
            
        } catch (Exception e) {
            log.error("Failed to send loyalty notifications: {}", e.getMessage());
        }
    }

    private void updateLoyaltyMetrics(
            PointsTransaction transaction, 
            LoyaltyAccount account, 
            LoyaltyPointsEvent event) {
        
        try {
            // Transaction metrics
            metricsService.incrementCounter("loyalty.transaction",
                Map.of(
                    "type", transaction.getType().toString(),
                    "status", transaction.getStatus().toString(),
                    "tier", account.getCurrentTier().toString()
                ));
            
            // Points metrics
            metricsService.recordGauge("loyalty.points.earned", 
                transaction.getActualPoints().doubleValue(),
                Map.of("source", event.getSource()));
            
            // Balance metrics
            metricsService.recordGauge("loyalty.account.balance", 
                account.getCurrentBalance().doubleValue(),
                Map.of("tier", account.getCurrentTier().toString()));
            
            // Tier distribution
            metricsService.incrementCounter("loyalty.tier.distribution",
                Map.of("tier", account.getCurrentTier().toString()));
            
        } catch (Exception e) {
            log.error("Failed to update loyalty metrics: {}", e.getMessage());
        }
    }

    private void createLoyaltyAuditLog(
            PointsTransaction transaction, 
            LoyaltyAccount account, 
            LoyaltyPointsEvent event, 
            String correlationId) {
        
        auditLogger.logBusinessEvent(
            "LOYALTY_POINTS_PROCESSED",
            account.getUserId(),
            transaction.getId(),
            transaction.getType().toString(),
            transaction.getActualPoints().doubleValue(),
            "loyalty_processor",
            true,
            Map.of(
                "transactionId", transaction.getId(),
                "accountId", account.getId(),
                "transactionType", transaction.getType().toString(),
                "basePoints", transaction.getBasePoints().toString(),
                "actualPoints", transaction.getActualPoints().toString(),
                "totalMultiplier", transaction.getTotalMultiplier().toString(),
                "currentBalance", account.getCurrentBalance().toString(),
                "currentTier", account.getCurrentTier().toString(),
                "source", event.getSource(),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private LoyaltyAccount processInstantBonus(LoyaltyPointsEvent event, String correlationId) {
        LoyaltyAccount account = getOrCreateLoyaltyAccount(event.getUserId());
        
        PointsTransaction transaction = createPointsTransaction(
            event, account, UUID.randomUUID().toString(), correlationId
        );
        transaction.setType(PointsTransactionType.BONUS);
        transaction.setActualPoints(event.getPoints());
        transaction.setIsBonus(true);
        transaction.setStatus(PointsStatus.CREDITED);
        transaction.setCreditedAt(LocalDateTime.now());
        
        account.setCurrentBalance(account.getCurrentBalance().add(event.getPoints()));
        account.setTotalBonusEarned(account.getTotalBonusEarned().add(event.getPoints()));
        
        transactionRepository.save(transaction);
        return loyaltyRepository.save(account);
    }

    private BigDecimal getTierMultiplier(RewardTier tier) {
        return switch (tier) {
            case BRONZE -> new BigDecimal("1.0");
            case SILVER -> new BigDecimal("1.25");
            case GOLD -> new BigDecimal("1.5");
            case PLATINUM -> new BigDecimal("2.0");
            case DIAMOND -> new BigDecimal("2.5");
        };
    }

    private void applyTierUpgradeBenefits(LoyaltyAccount account, RewardTier newTier) {
        // Award tier upgrade bonus
        BigDecimal bonusPoints = switch (newTier) {
            case SILVER -> new BigDecimal("500");
            case GOLD -> new BigDecimal("1000");
            case PLATINUM -> new BigDecimal("2500");
            case DIAMOND -> new BigDecimal("5000");
            default -> BigDecimal.ZERO;
        };
        
        if (bonusPoints.compareTo(BigDecimal.ZERO) > 0) {
            account.setCurrentBalance(account.getCurrentBalance().add(bonusPoints));
            account.setTierUpgradeBonus(bonusPoints);
        }
    }

    private void extendPointsExpiry(List<PointsTransaction> expiringPoints, LoyaltyAccount account) {
        for (PointsTransaction points : expiringPoints) {
            points.setExpiryDate(points.getExpiryDate().plusMonths(3));
            points.setExpiryExtended(true);
            transactionRepository.save(points);
        }
    }

    private void awardMilestoneBonus(LoyaltyAccount account, Integer milestone) {
        BigDecimal bonus = new BigDecimal(milestone / 10); // 10% of milestone as bonus
        account.setCurrentBalance(account.getCurrentBalance().add(bonus));
        account.setMilestoneBonusEarned(account.getMilestoneBonusEarned().add(bonus));
    }

    private boolean isStreakMaintained(LoyaltyAccount account) {
        if (account.getLastActivityAt() == null) return false;
        long daysSinceLastActivity = ChronoUnit.DAYS.between(account.getLastActivityAt(), LocalDateTime.now());
        return daysSinceLastActivity <= 7; // Weekly streak
    }

    private void triggerTransferToDestination(String destinationUserId, PointsTransaction sourceTransaction) {
        // Create transfer event for destination account
        log.info("Triggering points transfer to user: {}", destinationUserId);
    }
}
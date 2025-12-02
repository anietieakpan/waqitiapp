package com.waqiti.rewards.kafka;

import com.waqiti.common.events.LoyaltyPointsAccrualEvent;
import com.waqiti.common.events.PointsAccruedEvent;
import com.waqiti.rewards.domain.LoyaltyAccount;
import com.waqiti.rewards.domain.PointsTransaction;
import com.waqiti.rewards.domain.PointsTransactionType;
import com.waqiti.rewards.domain.PointsTransactionStatus;
import com.waqiti.rewards.domain.RewardTier;
import com.waqiti.rewards.domain.CampaignBonus;
import com.waqiti.rewards.repository.LoyaltyAccountRepository;
import com.waqiti.rewards.repository.PointsTransactionRepository;
import com.waqiti.rewards.service.LoyaltyProgramService;
import com.waqiti.rewards.service.PointsCalculationService;
import com.waqiti.rewards.service.TierManagementService;
import com.waqiti.rewards.service.ComplianceService;
import com.waqiti.rewards.service.NotificationService;
import com.waqiti.rewards.service.AuditService;
import com.waqiti.rewards.service.CampaignService;
import com.waqiti.rewards.service.FraudDetectionService;
import com.waqiti.rewards.service.ExpirationService;
import com.waqiti.rewards.exception.LoyaltyException;
import com.waqiti.rewards.exception.ComplianceViolationException;
import com.waqiti.rewards.exception.PointsCalculationException;
import com.waqiti.common.security.encryption.EncryptionService;
import com.waqiti.common.compliance.ComplianceValidator;
import com.waqiti.common.audit.AuditEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * CRITICAL LOYALTY POINTS ACCRUAL EVENT CONSUMER - Consumer 46
 * 
 * Processes loyalty points accrual events with zero-tolerance 12-step processing:
 * 1. Event validation and sanitization
 * 2. Idempotency and duplicate detection
 * 3. Regulatory compliance verification
 * 4. Loyalty account validation and verification
 * 5. Transaction eligibility and qualification check
 * 6. Points calculation and bonus determination
 * 7. Campaign and promotion evaluation
 * 8. Tier progression and benefit calculation
 * 9. Fraud detection and validation
 * 10. Points accrual and balance update
 * 11. Audit trail and transaction recording
 * 12. Notification dispatch and tier updates
 * 
 * REGULATORY COMPLIANCE:
 * - Consumer Protection regulations
 * - Tax reporting for rewards (1099-MISC)
 * - Data privacy regulations (GDPR/CCPA)
 * - Financial services regulations
 * - Loyalty program disclosure requirements
 * - Anti-Money Laundering (AML) monitoring
 * 
 * ACCRUAL SOURCES SUPPORTED:
 * - Transaction-based earnings
 * - Referral bonuses
 * - Campaign participation
 * - Partner merchant purchases
 * - Social engagement activities
 * - Welcome bonuses
 * - Tier advancement bonuses
 * 
 * SLA: 99.99% uptime, <5s processing time
 * 
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Validated
public class LoyaltyPointsAccrualEventConsumer {
    
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final PointsTransactionRepository pointsTransactionRepository;
    private final LoyaltyProgramService loyaltyProgramService;
    private final PointsCalculationService pointsCalculationService;
    private final TierManagementService tierManagementService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final CampaignService campaignService;
    private final FraudDetectionService fraudDetectionService;
    private final ExpirationService expirationService;
    private final EncryptionService encryptionService;
    private final ComplianceValidator complianceValidator;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String POINTS_ACCRUED_TOPIC = "points-accrued-events";
    private static final String TIER_UPGRADED_TOPIC = "tier-upgraded-events";
    private static final String CAMPAIGN_MILESTONE_TOPIC = "campaign-milestone-events";
    private static final String FRAUD_ALERT_TOPIC = "fraud-alert-events";
    private static final String DLQ_TOPIC = "loyalty-points-accrual-events-dlq";
    
    private static final int MAX_POINTS_PER_TRANSACTION = 100000;
    private static final int MIN_POINTS_PER_ACCRUAL = 1;
    private static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("100000.00");
    private static final int MAX_DAILY_ACCRUALS_PER_USER = 50;
    private static final BigDecimal TAX_REPORTING_THRESHOLD = new BigDecimal("600.00");

    @KafkaListener(
        topics = "loyalty-points-accrual-events",
        groupId = "loyalty-points-accrual-processor",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "5"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(
        value = {LoyaltyException.class, PointsCalculationException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 5000)
    )
    public void handleLoyaltyPointsAccrualEvent(
            @Payload @Valid LoyaltyPointsAccrualEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {
        
        String correlationId = generateCorrelationId(event, partition, offset);
        long processingStartTime = System.currentTimeMillis();
        
        log.info("STEP 1: Processing loyalty points accrual event - ID: {}, User: {}, Source: {}, Amount: {}, Correlation: {}",
            event.getAccrualId(), event.getUserId(), event.getAccrualSource(), event.getTransactionAmount(), correlationId);
        
        try {
            // STEP 1: Event validation and sanitization
            validateAndSanitizeEvent(event, correlationId);
            
            // STEP 2: Idempotency and duplicate detection
            if (checkIdempotencyAndDuplicates(event, correlationId)) {
                acknowledgeAndReturn(acknowledgment, "Duplicate loyalty points accrual event detected");
                return;
            }
            
            // STEP 3: Regulatory compliance verification
            performComplianceVerification(event, correlationId);
            
            // STEP 4: Loyalty account validation and verification
            LoyaltyAccount loyaltyAccount = validateLoyaltyAccountAndVerification(event, correlationId);
            
            // STEP 5: Transaction eligibility and qualification check
            EligibilityCheckResult eligibilityResult = performTransactionEligibilityAndQualificationCheck(event, loyaltyAccount, correlationId);
            
            // STEP 6: Points calculation and bonus determination
            PointsCalculationResult pointsResult = calculatePointsAndDetermineBonuses(event, loyaltyAccount, eligibilityResult, correlationId);
            
            // STEP 7: Campaign and promotion evaluation
            CampaignEvaluationResult campaignResult = evaluateCampaignsAndPromotions(event, loyaltyAccount, pointsResult, correlationId);
            
            // STEP 8: Tier progression and benefit calculation
            TierProgressionResult tierResult = calculateTierProgressionAndBenefits(event, loyaltyAccount, pointsResult, campaignResult, correlationId);
            
            // STEP 9: Fraud detection and validation
            FraudDetectionResult fraudResult = performFraudDetectionAndValidation(event, loyaltyAccount, pointsResult, correlationId);
            
            // STEP 10: Points accrual and balance update
            AccrualResult accrualResult = processPointsAccrualAndBalanceUpdate(event, loyaltyAccount, pointsResult, campaignResult, correlationId);
            
            // STEP 11: Audit trail and transaction recording
            PointsTransaction transaction = createAuditTrailAndRecordTransaction(event, loyaltyAccount, eligibilityResult,
                pointsResult, campaignResult, tierResult, fraudResult, accrualResult, correlationId, processingStartTime);
            
            // STEP 12: Notification dispatch and tier updates
            dispatchNotificationsAndTierUpdates(event, loyaltyAccount, transaction, tierResult, campaignResult, correlationId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            long processingTime = System.currentTimeMillis() - processingStartTime;
            log.info("Successfully processed loyalty points accrual - ID: {}, Points: {}, New Balance: {}, Tier: {}, Time: {}ms, Correlation: {}",
                event.getAccrualId(), accrualResult.getTotalPointsAccrued(), accrualResult.getNewBalance(), 
                tierResult.getNewTier(), processingTime, correlationId);
            
        } catch (ComplianceViolationException e) {
            handleComplianceViolation(event, e, correlationId, acknowledgment);
        } catch (PointsCalculationException e) {
            handlePointsCalculationError(event, e, correlationId, acknowledgment);
        } catch (LoyaltyException e) {
            handleLoyaltyError(event, e, correlationId, acknowledgment);
        } catch (Exception e) {
            handleCriticalError(event, e, correlationId, acknowledgment);
        }
    }

    /**
     * STEP 1: Event validation and sanitization
     */
    private void validateAndSanitizeEvent(LoyaltyPointsAccrualEvent event, String correlationId) {
        log.debug("STEP 1: Validating loyalty points accrual event - Correlation: {}", correlationId);
        
        if (event == null) {
            throw new IllegalArgumentException("Loyalty points accrual event cannot be null");
        }
        
        if (event.getAccrualId() == null || event.getAccrualId().trim().isEmpty()) {
            throw new IllegalArgumentException("Accrual ID is required");
        }
        
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (event.getAccrualSource() == null || event.getAccrualSource().trim().isEmpty()) {
            throw new IllegalArgumentException("Accrual source is required");
        }
        
        if (event.getTransactionAmount() != null) {
            if (event.getTransactionAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Transaction amount cannot be negative");
            }
            if (event.getTransactionAmount().compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
                throw new LoyaltyException("Transaction amount exceeds maximum: " + MAX_TRANSACTION_AMOUNT);
            }
        }
        
        if (event.getBasePoints() != null) {
            if (event.getBasePoints() < 0) {
                throw new IllegalArgumentException("Base points cannot be negative");
            }
            if (event.getBasePoints() > MAX_POINTS_PER_TRANSACTION) {
                throw new LoyaltyException("Base points exceed maximum: " + MAX_POINTS_PER_TRANSACTION);
            }
        }
        
        // Sanitize string fields
        event.setAccrualId(sanitizeString(event.getAccrualId()));
        event.setUserId(sanitizeString(event.getUserId()));
        event.setAccrualSource(sanitizeString(event.getAccrualSource().toUpperCase()));
        event.setTransactionId(sanitizeString(event.getTransactionId()));
        event.setMerchantId(sanitizeString(event.getMerchantId()));
        event.setCurrency(sanitizeString(event.getCurrency()));
        
        log.debug("STEP 1: Event validation completed - Source: {}, Amount: {}, Correlation: {}",
            event.getAccrualSource(), event.getTransactionAmount(), correlationId);
    }

    /**
     * STEP 2: Idempotency and duplicate detection
     */
    private boolean checkIdempotencyAndDuplicates(LoyaltyPointsAccrualEvent event, String correlationId) {
        log.debug("STEP 2: Checking idempotency - Correlation: {}", correlationId);
        
        // Check for existing points transaction
        boolean isDuplicate = pointsTransactionRepository.existsByAccrualIdAndUserId(
            event.getAccrualId(), event.getUserId());
        
        if (isDuplicate) {
            log.warn("Duplicate loyalty points accrual detected - Accrual: {}, User: {}, Correlation: {}",
                event.getAccrualId(), event.getUserId(), correlationId);
            
            auditService.logEvent(AuditEventType.DUPLICATE_POINTS_ACCRUAL_DETECTED, 
                event.getUserId(), event.getAccrualId(), correlationId);
        }
        
        // Additional check for transaction-based accruals
        if (event.getTransactionId() != null && !event.getTransactionId().trim().isEmpty()) {
            boolean transactionDuplicate = pointsTransactionRepository.existsByTransactionIdAndUserId(
                event.getTransactionId(), event.getUserId());
            
            if (transactionDuplicate) {
                log.warn("Transaction already processed for points - Transaction: {}, User: {}, Correlation: {}",
                    event.getTransactionId(), event.getUserId(), correlationId);
                return true;
            }
        }
        
        return isDuplicate;
    }

    /**
     * STEP 3: Regulatory compliance verification
     */
    private void performComplianceVerification(LoyaltyPointsAccrualEvent event, String correlationId) {
        log.debug("STEP 3: Performing compliance verification - Correlation: {}", correlationId);
        
        // User eligibility verification
        if (!complianceService.isUserEligibleForLoyalty(event.getUserId())) {
            throw new ComplianceViolationException("User not eligible for loyalty program: " + event.getUserId());
        }
        
        // Geographic restrictions
        if (!complianceService.isGeographicallyEligible(event.getUserId(), event.getCountryCode())) {
            throw new ComplianceViolationException("Geographic restrictions apply for loyalty program");
        }
        
        // Age verification for certain rewards
        if (!complianceService.isAgeCompliant(event.getUserId(), event.getAccrualSource())) {
            throw new ComplianceViolationException("Age restrictions apply for this reward type");
        }
        
        // Tax implications check
        if (event.getTransactionAmount() != null && 
            event.getTransactionAmount().compareTo(TAX_REPORTING_THRESHOLD) >= 0) {
            complianceService.flagForTaxReporting(event.getUserId(), event.getTransactionAmount(), correlationId);
        }
        
        // Terms and conditions acceptance
        if (!complianceService.hasAcceptedLoyaltyTerms(event.getUserId())) {
            throw new ComplianceViolationException("User has not accepted loyalty program terms");
        }
        
        log.debug("STEP 3: Compliance verification completed - Correlation: {}", correlationId);
    }

    /**
     * STEP 4: Loyalty account validation and verification
     */
    private LoyaltyAccount validateLoyaltyAccountAndVerification(LoyaltyPointsAccrualEvent event, String correlationId) {
        log.debug("STEP 4: Validating loyalty account - Correlation: {}", correlationId);
        
        Optional<LoyaltyAccount> accountOpt = loyaltyAccountRepository.findByUserId(event.getUserId());
        LoyaltyAccount loyaltyAccount;
        
        if (accountOpt.isEmpty()) {
            // Auto-create loyalty account for new users
            loyaltyAccount = loyaltyProgramService.createLoyaltyAccount(event.getUserId(), correlationId);
            log.info("Created new loyalty account for user: {}, Correlation: {}", event.getUserId(), correlationId);
        } else {
            loyaltyAccount = accountOpt.get();
        }
        
        // Verify account status
        if (!loyaltyAccount.isActive()) {
            throw new LoyaltyException("Loyalty account is not active: " + event.getUserId());
        }
        
        if (loyaltyAccount.isSuspended()) {
            throw new LoyaltyException("Loyalty account is suspended: " + event.getUserId());
        }
        
        // Check account limits
        if (loyaltyAccount.getTotalPointsEarned() >= loyaltyAccount.getMaxLifetimePoints()) {
            throw new LoyaltyException("Maximum lifetime points reached: " + loyaltyAccount.getMaxLifetimePoints());
        }
        
        log.debug("STEP 4: Loyalty account validated - Balance: {}, Tier: {}, Correlation: {}",
            loyaltyAccount.getPointsBalance(), loyaltyAccount.getCurrentTier(), correlationId);
        
        return loyaltyAccount;
    }

    /**
     * STEP 5: Transaction eligibility and qualification check
     */
    private EligibilityCheckResult performTransactionEligibilityAndQualificationCheck(LoyaltyPointsAccrualEvent event,
            LoyaltyAccount loyaltyAccount, String correlationId) {
        log.debug("STEP 5: Checking transaction eligibility - Correlation: {}", correlationId);
        
        EligibilityCheckResult result = loyaltyProgramService.checkTransactionEligibility(
            event, loyaltyAccount);
        
        if (!result.isEligible()) {
            throw new LoyaltyException("Transaction not eligible for points: " + result.getIneligibilityReason());
        }
        
        // Check velocity limits
        int dailyAccruals = pointsTransactionRepository.countByUserIdAndCreatedAtAfter(
            event.getUserId(), LocalDateTime.now().minusDays(1));
        
        if (dailyAccruals >= MAX_DAILY_ACCRUALS_PER_USER) {
            throw new LoyaltyException("Daily points accrual limit exceeded: " + MAX_DAILY_ACCRUALS_PER_USER);
        }
        
        result.setDailyAccrualCount(dailyAccruals);
        
        // Check minimum transaction requirements
        if (event.getTransactionAmount() != null && result.getMinimumTransactionAmount() != null) {
            if (event.getTransactionAmount().compareTo(result.getMinimumTransactionAmount()) < 0) {
                throw new LoyaltyException("Transaction amount below minimum for points: " + result.getMinimumTransactionAmount());
            }
        }
        
        // Check category/merchant eligibility
        if (event.getMerchantId() != null && !loyaltyProgramService.isMerchantEligible(event.getMerchantId())) {
            throw new LoyaltyException("Merchant not eligible for loyalty program: " + event.getMerchantId());
        }
        
        log.debug("STEP 5: Eligibility check completed - Eligible: {}, Daily Count: {}, Correlation: {}",
            result.isEligible(), dailyAccruals, correlationId);
        
        return result;
    }

    /**
     * STEP 6: Points calculation and bonus determination
     */
    private PointsCalculationResult calculatePointsAndDetermineBonuses(LoyaltyPointsAccrualEvent event,
            LoyaltyAccount loyaltyAccount, EligibilityCheckResult eligibilityResult, String correlationId) {
        log.debug("STEP 6: Calculating points - Correlation: {}", correlationId);
        
        PointsCalculationResult result = pointsCalculationService.calculatePoints(
            event, loyaltyAccount, eligibilityResult);
        
        // Base points calculation
        int basePoints = 0;
        if (event.getBasePoints() != null) {
            basePoints = event.getBasePoints();
        } else if (event.getTransactionAmount() != null) {
            // Default: 1 point per dollar spent
            basePoints = event.getTransactionAmount().intValue();
        }
        
        // Apply tier multiplier
        double tierMultiplier = tierManagementService.getTierMultiplier(loyaltyAccount.getCurrentTier());
        int tierBonusPoints = (int) Math.round(basePoints * (tierMultiplier - 1.0));
        
        // Apply category bonuses
        int categoryBonusPoints = 0;
        if (event.getCategory() != null) {
            double categoryMultiplier = loyaltyProgramService.getCategoryMultiplier(event.getCategory());
            categoryBonusPoints = (int) Math.round(basePoints * (categoryMultiplier - 1.0));
        }
        
        // Apply merchant bonuses
        int merchantBonusPoints = 0;
        if (event.getMerchantId() != null) {
            merchantBonusPoints = loyaltyProgramService.getMerchantBonusPoints(event.getMerchantId(), basePoints);
        }
        
        // Welcome bonus for new accounts
        int welcomeBonusPoints = 0;
        if (loyaltyAccount.getTotalPointsEarned() == 0 && "FIRST_TRANSACTION".equals(event.getAccrualSource())) {
            welcomeBonusPoints = loyaltyProgramService.getWelcomeBonusPoints();
        }
        
        // Calculate total points
        int totalPoints = basePoints + tierBonusPoints + categoryBonusPoints + merchantBonusPoints + welcomeBonusPoints;
        
        // Validate points don't exceed maximum
        if (totalPoints > MAX_POINTS_PER_TRANSACTION) {
            totalPoints = MAX_POINTS_PER_TRANSACTION;
            log.warn("Points calculation capped at maximum - User: {}, Calculated: {}, Capped: {}, Correlation: {}",
                event.getUserId(), basePoints + tierBonusPoints + categoryBonusPoints + merchantBonusPoints + welcomeBonusPoints,
                MAX_POINTS_PER_TRANSACTION, correlationId);
        }
        
        result.setBasePoints(basePoints);
        result.setTierBonusPoints(tierBonusPoints);
        result.setCategoryBonusPoints(categoryBonusPoints);
        result.setMerchantBonusPoints(merchantBonusPoints);
        result.setWelcomeBonusPoints(welcomeBonusPoints);
        result.setTotalPoints(totalPoints);
        result.setTierMultiplier(tierMultiplier);
        
        log.debug("STEP 6: Points calculation completed - Base: {}, Bonuses: {}, Total: {}, Correlation: {}",
            basePoints, totalPoints - basePoints, totalPoints, correlationId);
        
        return result;
    }

    /**
     * STEP 7: Campaign and promotion evaluation
     */
    private CampaignEvaluationResult evaluateCampaignsAndPromotions(LoyaltyPointsAccrualEvent event,
            LoyaltyAccount loyaltyAccount, PointsCalculationResult pointsResult, String correlationId) {
        log.debug("STEP 7: Evaluating campaigns - Correlation: {}", correlationId);
        
        CampaignEvaluationResult result = campaignService.evaluateCampaigns(
            event, loyaltyAccount, pointsResult);
        
        // Check active campaigns
        List<CampaignBonus> applicableCampaigns = campaignService.getApplicableCampaigns(
            event.getUserId(), event.getAccrualSource(), event.getCategory(), event.getMerchantId());
        
        int campaignBonusPoints = 0;
        List<String> appliedCampaigns = new ArrayList<>();
        
        for (CampaignBonus campaign : applicableCampaigns) {
            if (campaignService.isCampaignEligible(campaign, event, loyaltyAccount)) {
                int bonusPoints = campaignService.calculateCampaignBonus(campaign, pointsResult.getTotalPoints());
                campaignBonusPoints += bonusPoints;
                appliedCampaigns.add(campaign.getCampaignId());
                
                // Track campaign participation
                campaignService.recordCampaignParticipation(campaign.getCampaignId(), event.getUserId(), bonusPoints, correlationId);
                
                log.debug("Applied campaign bonus - Campaign: {}, Bonus: {}, User: {}, Correlation: {}",
                    campaign.getCampaignId(), bonusPoints, event.getUserId(), correlationId);
            }
        }
        
        result.setCampaignBonusPoints(campaignBonusPoints);
        result.setAppliedCampaigns(appliedCampaigns);
        result.setTotalPointsWithCampaigns(pointsResult.getTotalPoints() + campaignBonusPoints);
        
        // Check for campaign milestones
        List<String> milestonesCrossed = campaignService.checkMilestones(event.getUserId(), result.getTotalPointsWithCampaigns());
        result.setMilestonesCrossed(milestonesCrossed);
        
        log.debug("STEP 7: Campaign evaluation completed - Campaigns: {}, Bonus: {}, Total: {}, Correlation: {}",
            appliedCampaigns.size(), campaignBonusPoints, result.getTotalPointsWithCampaigns(), correlationId);
        
        return result;
    }

    /**
     * STEP 8: Tier progression and benefit calculation
     */
    private TierProgressionResult calculateTierProgressionAndBenefits(LoyaltyPointsAccrualEvent event,
            LoyaltyAccount loyaltyAccount, PointsCalculationResult pointsResult, CampaignEvaluationResult campaignResult, String correlationId) {
        log.debug("STEP 8: Calculating tier progression - Correlation: {}", correlationId);
        
        TierProgressionResult result = tierManagementService.calculateTierProgression(
            loyaltyAccount, campaignResult.getTotalPointsWithCampaigns());
        
        RewardTier currentTier = loyaltyAccount.getCurrentTier();
        int newTotalPoints = loyaltyAccount.getTotalPointsEarned() + campaignResult.getTotalPointsWithCampaigns();
        
        RewardTier newTier = tierManagementService.calculateNewTier(newTotalPoints, loyaltyAccount.getCurrentTier());
        
        result.setPreviousTier(currentTier);
        result.setNewTier(newTier);
        result.setTierChanged(!currentTier.equals(newTier));
        
        // Calculate tier advancement bonus
        int tierAdvancementBonus = 0;
        if (result.isTierChanged()) {
            tierAdvancementBonus = tierManagementService.getTierAdvancementBonus(currentTier, newTier);
            result.setTierAdvancementBonus(tierAdvancementBonus);
            
            // Log tier advancement
            log.info("User tier advancement - User: {}, From: {}, To: {}, Bonus: {}, Correlation: {}",
                event.getUserId(), currentTier, newTier, tierAdvancementBonus, correlationId);
        }
        
        // Calculate points needed for next tier
        RewardTier nextTier = tierManagementService.getNextTier(newTier);
        if (nextTier != null) {
            int pointsForNextTier = tierManagementService.getPointsRequiredForTier(nextTier);
            int pointsNeeded = Math.max(0, pointsForNextTier - newTotalPoints - tierAdvancementBonus);
            result.setPointsNeededForNextTier(pointsNeeded);
            result.setNextTier(nextTier);
        }
        
        // Update final points total with tier advancement bonus
        result.setFinalTotalPoints(campaignResult.getTotalPointsWithCampaigns() + tierAdvancementBonus);
        
        log.debug("STEP 8: Tier progression completed - Current: {}, New: {}, Bonus: {}, Final: {}, Correlation: {}",
            currentTier, newTier, tierAdvancementBonus, result.getFinalTotalPoints(), correlationId);
        
        return result;
    }

    /**
     * STEP 9: Fraud detection and validation
     */
    private FraudDetectionResult performFraudDetectionAndValidation(LoyaltyPointsAccrualEvent event,
            LoyaltyAccount loyaltyAccount, PointsCalculationResult pointsResult, String correlationId) {
        log.debug("STEP 9: Performing fraud detection - Correlation: {}", correlationId);
        
        FraudDetectionResult result = fraudDetectionService.detectLoyaltyFraud(
            event, loyaltyAccount, pointsResult);
        
        // Check for suspicious patterns
        boolean suspiciousActivity = fraudDetectionService.checkSuspiciousPatterns(
            event.getUserId(), event.getAccrualSource(), pointsResult.getTotalPoints());
        
        if (suspiciousActivity) {
            result.setSuspicious(true);
            result.addFraudIndicator("SUSPICIOUS_ACTIVITY_PATTERN");
        }
        
        // Velocity checks
        if (pointsResult.getTotalPoints() > 10000) { // High points threshold
            boolean velocityFraud = fraudDetectionService.checkVelocityFraud(event.getUserId(), pointsResult.getTotalPoints());
            if (velocityFraud) {
                result.setFraudulent(true);
                result.addFraudIndicator("VELOCITY_FRAUD");
            }
        }
        
        // Cross-reference with known fraud patterns
        if (fraudDetectionService.matchesFraudPattern(event, loyaltyAccount)) {
            result.setFraudulent(true);
            result.addFraudIndicator("FRAUD_PATTERN_MATCH");
        }
        
        // If fraud detected, block the accrual
        if (result.isFraudulent()) {
            throw new LoyaltyException("Fraudulent points accrual detected: " + result.getFraudIndicators());
        }
        
        // Enhanced monitoring for suspicious activity
        if (result.isSuspicious()) {
            fraudDetectionService.enableEnhancedMonitoring(event.getUserId(), correlationId);
            log.warn("Suspicious loyalty activity detected - User: {}, Indicators: {}, Correlation: {}",
                event.getUserId(), result.getFraudIndicators(), correlationId);
        }
        
        log.debug("STEP 9: Fraud detection completed - Fraudulent: {}, Suspicious: {}, Correlation: {}",
            result.isFraudulent(), result.isSuspicious(), correlationId);
        
        return result;
    }

    /**
     * STEP 10: Points accrual and balance update
     */
    private AccrualResult processPointsAccrualAndBalanceUpdate(LoyaltyPointsAccrualEvent event,
            LoyaltyAccount loyaltyAccount, PointsCalculationResult pointsResult, CampaignEvaluationResult campaignResult, String correlationId) {
        log.debug("STEP 10: Processing points accrual - Correlation: {}", correlationId);
        
        AccrualResult result = new AccrualResult();
        
        int totalPointsToAccrue = campaignResult.getTotalPointsWithCampaigns();
        int previousBalance = loyaltyAccount.getPointsBalance();
        int newBalance = previousBalance + totalPointsToAccrue;
        
        // Set expiration date for new points
        LocalDate expirationDate = expirationService.calculateExpirationDate(
            loyaltyAccount.getCurrentTier(), LocalDate.now());
        
        // Update loyalty account
        loyaltyAccount.setPointsBalance(newBalance);
        loyaltyAccount.setTotalPointsEarned(loyaltyAccount.getTotalPointsEarned() + totalPointsToAccrue);
        loyaltyAccount.setLastAccrualDate(LocalDateTime.now());
        loyaltyAccount.setLastTransactionId(event.getTransactionId());
        
        loyaltyAccountRepository.save(loyaltyAccount);
        
        result.setTotalPointsAccrued(totalPointsToAccrue);
        result.setPreviousBalance(previousBalance);
        result.setNewBalance(newBalance);
        result.setExpirationDate(expirationDate);
        result.setSuccessful(true);
        
        // Schedule points expiration if applicable
        if (expirationDate != null) {
            expirationService.schedulePointsExpiration(
                loyaltyAccount.getId(), totalPointsToAccrue, expirationDate, correlationId);
        }
        
        log.debug("STEP 10: Points accrual completed - Accrued: {}, New Balance: {}, Expires: {}, Correlation: {}",
            totalPointsToAccrue, newBalance, expirationDate, correlationId);
        
        return result;
    }

    /**
     * STEP 11: Audit trail and transaction recording
     */
    private PointsTransaction createAuditTrailAndRecordTransaction(LoyaltyPointsAccrualEvent event,
            LoyaltyAccount loyaltyAccount, EligibilityCheckResult eligibilityResult, PointsCalculationResult pointsResult,
            CampaignEvaluationResult campaignResult, TierProgressionResult tierResult, FraudDetectionResult fraudResult,
            AccrualResult accrualResult, String correlationId, long processingStartTime) {
        log.debug("STEP 11: Creating audit trail - Correlation: {}", correlationId);
        
        PointsTransaction transaction = PointsTransaction.builder()
            .accrualId(event.getAccrualId())
            .loyaltyAccountId(loyaltyAccount.getId())
            .userId(event.getUserId())
            .transactionId(event.getTransactionId())
            .type(PointsTransactionType.ACCRUAL)
            .status(PointsTransactionStatus.COMPLETED)
            .accrualSource(event.getAccrualSource())
            .transactionAmount(event.getTransactionAmount())
            .currency(event.getCurrency())
            .basePoints(pointsResult.getBasePoints())
            .tierBonusPoints(pointsResult.getTierBonusPoints())
            .categoryBonusPoints(pointsResult.getCategoryBonusPoints())
            .merchantBonusPoints(pointsResult.getMerchantBonusPoints())
            .campaignBonusPoints(campaignResult.getCampaignBonusPoints())
            .tierAdvancementBonus(tierResult.getTierAdvancementBonus())
            .totalPointsAccrued(accrualResult.getTotalPointsAccrued())
            .previousBalance(accrualResult.getPreviousBalance())
            .newBalance(accrualResult.getNewBalance())
            .expirationDate(accrualResult.getExpirationDate())
            .merchantId(event.getMerchantId())
            .category(event.getCategory())
            .countryCode(event.getCountryCode())
            .appliedCampaigns(String.join(",", campaignResult.getAppliedCampaigns()))
            .previousTier(tierResult.getPreviousTier())
            .newTier(tierResult.getNewTier())
            .tierChanged(tierResult.isTierChanged())
            .fraudScore(fraudResult.getFraudScore())
            .suspiciousActivity(fraudResult.isSuspicious())
            .description(generateTransactionDescription(event, pointsResult))
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .processingTimeMs(System.currentTimeMillis() - processingStartTime)
            .build();
        
        transaction = pointsTransactionRepository.save(transaction);
        
        // Create detailed audit log
        auditService.logLoyaltyPointsAccrualEvent(event, loyaltyAccount, transaction, eligibilityResult,
            pointsResult, campaignResult, tierResult, fraudResult, accrualResult, correlationId);
        
        log.debug("STEP 11: Audit trail created - Transaction ID: {}, Correlation: {}", transaction.getId(), correlationId);
        
        return transaction;
    }

    /**
     * STEP 12: Notification dispatch and tier updates
     */
    private void dispatchNotificationsAndTierUpdates(LoyaltyPointsAccrualEvent event, LoyaltyAccount loyaltyAccount,
            PointsTransaction transaction, TierProgressionResult tierResult, CampaignEvaluationResult campaignResult, String correlationId) {
        log.debug("STEP 12: Dispatching notifications - Correlation: {}", correlationId);
        
        // Send points accrual notification
        CompletableFuture.runAsync(() -> {
            notificationService.sendPointsAccrualNotification(
                event.getUserId(),
                transaction.getTotalPointsAccrued(),
                transaction.getNewBalance(),
                transaction.getExpirationDate()
            );
        });
        
        // Send tier advancement notification
        if (tierResult.isTierChanged()) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendTierAdvancementNotification(
                    event.getUserId(),
                    tierResult.getPreviousTier(),
                    tierResult.getNewTier(),
                    tierResult.getTierAdvancementBonus()
                );
            });
            
            // Publish tier upgraded event
            kafkaTemplate.send(TIER_UPGRADED_TOPIC, Map.of(
                "userId", event.getUserId(),
                "previousTier", tierResult.getPreviousTier().toString(),
                "newTier", tierResult.getNewTier().toString(),
                "advancementBonus", tierResult.getTierAdvancementBonus(),
                "correlationId", correlationId
            ));
        }
        
        // Send campaign milestone notifications
        for (String milestone : campaignResult.getMilestonesCrossed()) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCampaignMilestoneNotification(
                    event.getUserId(), milestone, correlationId);
            });
            
            // Publish campaign milestone event
            kafkaTemplate.send(CAMPAIGN_MILESTONE_TOPIC, Map.of(
                "userId", event.getUserId(),
                "milestone", milestone,
                "correlationId", correlationId
            ));
        }
        
        // Send push notification for significant point accruals
        if (transaction.getTotalPointsAccrued() >= 1000) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendPushNotification(
                    event.getUserId(),
                    "Points Earned!",
                    String.format("You just earned %d loyalty points! Total balance: %d points.",
                        transaction.getTotalPointsAccrued(), transaction.getNewBalance())
                );
            });
        }
        
        // Send balance update to user interface
        CompletableFuture.runAsync(() -> {
            notificationService.sendBalanceUpdate(
                event.getUserId(),
                transaction.getNewBalance(),
                tierResult.getNewTier(),
                tierResult.getPointsNeededForNextTier()
            );
        });
        
        // Publish points accrued event
        PointsAccruedEvent accruedEvent = PointsAccruedEvent.builder()
            .accrualId(event.getAccrualId())
            .userId(event.getUserId())
            .transactionId(event.getTransactionId())
            .accrualSource(event.getAccrualSource())
            .pointsAccrued(transaction.getTotalPointsAccrued())
            .newBalance(transaction.getNewBalance())
            .tier(tierResult.getNewTier().toString())
            .tierChanged(tierResult.isTierChanged())
            .campaignsApplied(campaignResult.getAppliedCampaigns())
            .correlationId(correlationId)
            .accruedAt(transaction.getCreatedAt())
            .build();
        
        kafkaTemplate.send(POINTS_ACCRUED_TOPIC, accruedEvent);
        
        log.debug("STEP 12: Notifications dispatched - Points: {}, Tier Change: {}, Campaigns: {}, Correlation: {}",
            transaction.getTotalPointsAccrued(), tierResult.isTierChanged(), campaignResult.getAppliedCampaigns().size(), correlationId);
    }

    // Error handling and utility methods
    private void handleComplianceViolation(LoyaltyPointsAccrualEvent event, ComplianceViolationException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Compliance violation in loyalty points accrual - Accrual: {}, Error: {}, Correlation: {}",
            event.getAccrualId(), e.getMessage(), correlationId);
        acknowledgment.acknowledge();
    }

    private void handlePointsCalculationError(LoyaltyPointsAccrualEvent event, PointsCalculationException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Points calculation error - Accrual: {}, Error: {}, Correlation: {}",
            event.getAccrualId(), e.getMessage(), correlationId);
        sendToDeadLetterQueue(event, e, correlationId);
        acknowledgment.acknowledge();
    }

    private void handleLoyaltyError(LoyaltyPointsAccrualEvent event, LoyaltyException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Loyalty error - Accrual: {}, Error: {}, Correlation: {}",
            event.getAccrualId(), e.getMessage(), correlationId);
        sendToDeadLetterQueue(event, e, correlationId);
        acknowledgment.acknowledge();
    }

    private void handleCriticalError(LoyaltyPointsAccrualEvent event, Exception e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Critical error in loyalty points accrual - Accrual: {}, Error: {}, Correlation: {}",
            event.getAccrualId(), e.getMessage(), e, correlationId);
        sendToDeadLetterQueue(event, e, correlationId);
        acknowledgment.acknowledge();
    }

    // Utility methods
    private String generateCorrelationId(LoyaltyPointsAccrualEvent event, int partition, long offset) {
        return String.format("loyalty-accrual-%s-p%d-o%d-%d",
            event.getAccrualId(), partition, offset, System.currentTimeMillis());
    }

    private String sanitizeString(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("[<>\"'&]", "");
    }

    private void acknowledgeAndReturn(Acknowledgment acknowledgment, String message) {
        log.info(message);
        acknowledgment.acknowledge();
    }

    private String generateTransactionDescription(LoyaltyPointsAccrualEvent event, PointsCalculationResult pointsResult) {
        return String.format("Points earned from %s - Base: %d points", 
            event.getAccrualSource().toLowerCase().replace("_", " "), pointsResult.getBasePoints());
    }

    private void sendToDeadLetterQueue(LoyaltyPointsAccrualEvent event, Exception error, String correlationId) {
        try {
            Map<String, Object> dlqMessage = Map.of(
                "originalEvent", event,
                "errorMessage", error.getMessage(),
                "errorClass", error.getClass().getName(),
                "correlationId", correlationId,
                "failedAt", Instant.now(),
                "service", "rewards-service"
            );
            
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            log.warn("Sent failed loyalty points accrual to DLQ - Accrual: {}, Correlation: {}",
                event.getAccrualId(), correlationId);
                
        } catch (Exception dlqError) {
            log.error("Failed to send loyalty points accrual to DLQ - Correlation: {}", correlationId, dlqError);
        }
    }

    // Inner classes for results (simplified for brevity)
    @lombok.Data
    @lombok.Builder
    private static class EligibilityCheckResult {
        private boolean eligible;
        private String ineligibilityReason;
        private BigDecimal minimumTransactionAmount;
        private int dailyAccrualCount;
    }

    @lombok.Data
    private static class PointsCalculationResult {
        private int basePoints;
        private int tierBonusPoints;
        private int categoryBonusPoints;
        private int merchantBonusPoints;
        private int welcomeBonusPoints;
        private int totalPoints;
        private double tierMultiplier;
    }

    @lombok.Data
    private static class CampaignEvaluationResult {
        private int campaignBonusPoints;
        private List<String> appliedCampaigns = new ArrayList<>();
        private int totalPointsWithCampaigns;
        private List<String> milestonesCrossed = new ArrayList<>();
    }

    @lombok.Data
    private static class TierProgressionResult {
        private RewardTier previousTier;
        private RewardTier newTier;
        private boolean tierChanged;
        private int tierAdvancementBonus;
        private int pointsNeededForNextTier;
        private RewardTier nextTier;
        private int finalTotalPoints;
    }

    @lombok.Data
    private static class FraudDetectionResult {
        private boolean fraudulent;
        private boolean suspicious;
        private int fraudScore;
        private List<String> fraudIndicators = new ArrayList<>();
        
        public void addFraudIndicator(String indicator) {
            fraudIndicators.add(indicator);
        }
    }

    @lombok.Data
    private static class AccrualResult {
        private int totalPointsAccrued;
        private int previousBalance;
        private int newBalance;
        private LocalDate expirationDate;
        private boolean successful;
    }
}
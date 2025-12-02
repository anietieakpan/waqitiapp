package com.waqiti.rewards.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.rewards.service.RewardsService;
import com.waqiti.rewards.service.WelcomeBonusService;
import com.waqiti.rewards.service.ReferralService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for account-activated-events
 * 
 * Processes account activation events to:
 * - Award welcome bonuses
 * - Initialize loyalty points
 * - Process referral rewards
 * - Setup rewards tier
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountActivatedEventsConsumer {
    
    private final RewardsService rewardsService;
    private final WelcomeBonusService welcomeBonusService;
    private final ReferralService referralService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = "account-activated-events",
        groupId = "rewards-service-account-activation-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleAccountActivated(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("Processing account activation event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        
        try {
            // Parse event
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            UUID userId = UUID.fromString((String) event.get("userId"));
            String accountType = (String) event.get("accountType");
            LocalDateTime activationDate = LocalDateTime.parse((String) event.get("activationDate"));
            String referralCode = (String) event.get("referralCode");
            String campaignId = (String) event.get("campaignId");
            
            log.info("Account activated - UserId: {}, AccountType: {}, ReferralCode: {}", 
                    userId, accountType, referralCode);
            
            // 1. Initialize rewards account
            initializeRewardsAccount(userId, accountType, activationDate);
            
            // 2. Award welcome bonus
            awardWelcomeBonus(userId, accountType, campaignId);
            
            // 3. Process referral rewards if applicable
            if (referralCode != null && !referralCode.trim().isEmpty()) {
                processReferralReward(userId, referralCode, accountType);
            }
            
            // 4. Setup initial rewards tier
            setupInitialRewardsTier(userId, accountType);
            
            // 5. Award activation bonus points
            awardActivationPoints(userId, accountType);
            
            // 6. Send welcome notification
            sendWelcomeRewardsNotification(userId, accountType);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Account activation rewards processed successfully - UserId: {}, ProcessingTime: {}ms", 
                    userId, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process account activation event: {}", eventJson, e);
            throw new RuntimeException("Account activation processing failed", e);
        }
    }
    
    private void initializeRewardsAccount(UUID userId, String accountType, LocalDateTime activationDate) {
        try {
            rewardsService.initializeRewardsAccount(userId, accountType, activationDate);
            log.info("Initialized rewards account for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to initialize rewards account for user: {}", userId, e);
            throw new RuntimeException("Rewards account initialization failed", e);
        }
    }
    
    private void awardWelcomeBonus(UUID userId, String accountType, String campaignId) {
        try {
            BigDecimal bonusAmount = welcomeBonusService.calculateWelcomeBonus(accountType, campaignId);
            
            if (bonusAmount != null && bonusAmount.compareTo(BigDecimal.ZERO) > 0) {
                welcomeBonusService.awardWelcomeBonus(userId, bonusAmount, campaignId);
                log.info("Awarded welcome bonus to user: {} - Amount: {}", userId, bonusAmount);
            }
        } catch (Exception e) {
            log.error("Failed to award welcome bonus to user: {}", userId, e);
        }
    }
    
    private void processReferralReward(UUID userId, String referralCode, String accountType) {
        try {
            boolean rewarded = referralService.processReferralReward(userId, referralCode, accountType);
            
            if (rewarded) {
                log.info("Processed referral reward for user: {} - ReferralCode: {}", userId, referralCode);
            } else {
                log.warn("Referral reward not processed for user: {} - ReferralCode: {}", userId, referralCode);
            }
        } catch (Exception e) {
            log.error("Failed to process referral reward for user: {}", userId, e);
        }
    }
    
    private void setupInitialRewardsTier(UUID userId, String accountType) {
        try {
            String initialTier = determineInitialTier(accountType);
            rewardsService.setupRewardsTier(userId, initialTier);
            log.info("Setup initial rewards tier for user: {} - Tier: {}", userId, initialTier);
        } catch (Exception e) {
            log.error("Failed to setup rewards tier for user: {}", userId, e);
        }
    }
    
    private void awardActivationPoints(UUID userId, String accountType) {
        try {
            int activationPoints = calculateActivationPoints(accountType);
            
            if (activationPoints > 0) {
                rewardsService.awardPoints(userId, activationPoints, "ACCOUNT_ACTIVATION", 
                        "Welcome points for activating account");
                log.info("Awarded activation points to user: {} - Points: {}", userId, activationPoints);
            }
        } catch (Exception e) {
            log.error("Failed to award activation points to user: {}", userId, e);
        }
    }
    
    private void sendWelcomeRewardsNotification(UUID userId, String accountType) {
        try {
            rewardsService.sendWelcomeNotification(userId, accountType);
            log.debug("Sent welcome rewards notification to user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to send welcome notification to user: {}", userId, e);
        }
    }
    
    private String determineInitialTier(String accountType) {
        return switch (accountType.toUpperCase()) {
            case "PREMIUM", "BUSINESS" -> "SILVER";
            case "ENTERPRISE" -> "GOLD";
            default -> "BRONZE";
        };
    }
    
    private int calculateActivationPoints(String accountType) {
        return switch (accountType.toUpperCase()) {
            case "PREMIUM" -> 500;
            case "BUSINESS" -> 1000;
            case "ENTERPRISE" -> 2000;
            default -> 100;
        };
    }
    
    @KafkaListener(
        topics = "account-activated-events.DLQ",
        groupId = "rewards-service-account-activation-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("Account activation event sent to DLQ - OriginalTopic: {}, Error: {}, Event: {}", 
                originalTopic, errorMessage, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID userId = event.containsKey("userId") ? UUID.fromString((String) event.get("userId")) : null;
            
            log.error("DLQ: Failed to process account activation for user: {} - Manual intervention required", userId);
            
        } catch (Exception e) {
            log.error("Failed to parse DLQ event: {}", eventJson, e);
        }
    }
}
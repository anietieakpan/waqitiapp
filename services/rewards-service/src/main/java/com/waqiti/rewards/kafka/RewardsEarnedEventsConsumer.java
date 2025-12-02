package com.waqiti.rewards.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.rewards.service.RewardsEarningService;
import com.waqiti.rewards.service.RewardsNotificationService;
import com.waqiti.common.audit.AuditService;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class RewardsEarnedEventsConsumer {
    
    private final RewardsEarningService rewardsEarningService;
    private final RewardsNotificationService rewardsNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"rewards-earned-events", "points-earned", "cashback-earned", "miles-earned"},
        groupId = "rewards-service-rewards-earned-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleRewardsEarnedEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("REWARDS EARNED: Processing rewards earned event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        UUID earningId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            earningId = UUID.fromString((String) event.get("earningId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            eventType = (String) event.get("eventType");
            String rewardType = (String) event.get("rewardType");
            BigDecimal rewardAmount = new BigDecimal(event.get("rewardAmount").toString());
            String rewardUnit = (String) event.getOrDefault("rewardUnit", "POINTS");
            String currency = (String) event.getOrDefault("currency", "USD");
            LocalDateTime earnedDate = LocalDateTime.parse((String) event.get("earnedDate"));
            String earnedCategory = (String) event.get("earnedCategory");
            String merchantId = (String) event.getOrDefault("merchantId", "");
            String merchantName = (String) event.getOrDefault("merchantName", "");
            BigDecimal transactionAmount = event.containsKey("transactionAmount") ? 
                    new BigDecimal(event.get("transactionAmount").toString()) : BigDecimal.ZERO;
            BigDecimal rewardRate = event.containsKey("rewardRate") ? 
                    new BigDecimal(event.get("rewardRate").toString()) : BigDecimal.ZERO;
            String campaignId = (String) event.getOrDefault("campaignId", "");
            String bonusType = (String) event.getOrDefault("bonusType", "");
            Integer multiplier = (Integer) event.getOrDefault("multiplier", 1);
            LocalDateTime expirationDate = event.containsKey("expirationDate") ? 
                    LocalDateTime.parse((String) event.get("expirationDate")) : null;
            
            log.info("Rewards earned event - EarningId: {}, CustomerId: {}, EventType: {}, Type: {}, Amount: {} {}, Category: {}", 
                    earningId, customerId, eventType, rewardType, rewardAmount, rewardUnit, earnedCategory);
            
            switch (eventType) {
                case "POINTS_EARNED" -> rewardsEarningService.processPointsEarned(earningId, customerId, 
                        transactionId, rewardAmount, earnedDate, earnedCategory, merchantId, 
                        transactionAmount, rewardRate, campaignId, bonusType, multiplier, expirationDate);
                
                case "CASHBACK_EARNED" -> rewardsEarningService.processCashbackEarned(earningId, 
                        customerId, transactionId, rewardAmount, currency, earnedDate, earnedCategory, 
                        merchantId, transactionAmount, rewardRate, campaignId);
                
                case "MILES_EARNED" -> rewardsEarningService.processMilesEarned(earningId, customerId, 
                        transactionId, rewardAmount, earnedDate, earnedCategory, merchantId, 
                        transactionAmount, rewardRate, campaignId, expirationDate);
                
                case "BONUS_EARNED" -> rewardsEarningService.processBonusEarned(earningId, customerId, 
                        transactionId, rewardType, rewardAmount, rewardUnit, earnedDate, bonusType, 
                        campaignId, multiplier);
                
                default -> log.warn("Unknown rewards earned event type: {}", eventType);
            }
            
            rewardsNotificationService.sendRewardsEarnedNotification(customerId, earningId, eventType, 
                    rewardType, rewardAmount, rewardUnit, earnedCategory, merchantName);
            
            rewardsEarningService.updateRewardsMetrics(eventType, rewardType, rewardAmount, earnedCategory, 
                    campaignId, bonusType, multiplier);
            
            auditService.auditFinancialEvent(
                    "REWARDS_EARNED_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Rewards earned event %s - Type: %s, Amount: %s %s, Category: %s", 
                            eventType, rewardType, rewardAmount, rewardUnit, earnedCategory),
                    Map.of(
                            "earningId", earningId.toString(),
                            "customerId", customerId.toString(),
                            "transactionId", transactionId != null ? transactionId.toString() : "NONE",
                            "eventType", eventType,
                            "rewardType", rewardType,
                            "rewardAmount", rewardAmount.toString(),
                            "rewardUnit", rewardUnit,
                            "earnedCategory", earnedCategory,
                            "merchantId", merchantId,
                            "campaignId", campaignId
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Rewards earned event processing failed - EarningId: {}, CustomerId: {}, EventType: {}, Error: {}", 
                    earningId, customerId, eventType, e.getMessage(), e);
            throw new RuntimeException("Rewards earned event processing failed", e);
        }
    }
}
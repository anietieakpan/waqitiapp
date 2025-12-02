package com.waqiti.rewards.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.rewards.service.RewardsRedemptionService;
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
public class RewardsRedeemedEventsConsumer {
    
    private final RewardsRedemptionService rewardsRedemptionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"rewards-redeemed-events", "points-redeemed", "cashback-redeemed"},
        groupId = "rewards-service-rewards-redeemed-group",
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
    public void handleRewardsRedeemedEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID redemptionId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            redemptionId = UUID.fromString((String) event.get("redemptionId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String rewardType = (String) event.get("rewardType");
            BigDecimal redemptionAmount = new BigDecimal(event.get("redemptionAmount").toString());
            String redemptionMethod = (String) event.get("redemptionMethod");
            LocalDateTime redemptionDate = LocalDateTime.parse((String) event.get("redemptionDate"));
            
            switch (eventType) {
                case "POINTS_REDEEMED" -> rewardsRedemptionService.processPointsRedeemed(redemptionId, 
                        customerId, redemptionAmount, redemptionMethod, redemptionDate);
                case "CASHBACK_REDEEMED" -> rewardsRedemptionService.processCashbackRedeemed(redemptionId, 
                        customerId, redemptionAmount, redemptionMethod, redemptionDate);
                default -> log.warn("Unknown redemption event type: {}", eventType);
            }
            
            auditService.auditFinancialEvent(
                    "REWARDS_REDEEMED_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Rewards redeemed event %s - Type: %s, Amount: %s", 
                            eventType, rewardType, redemptionAmount),
                    Map.of(
                            "redemptionId", redemptionId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "rewardType", rewardType,
                            "redemptionAmount", redemptionAmount.toString(),
                            "redemptionMethod", redemptionMethod
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Rewards redeemed event processing failed - RedemptionId: {}, CustomerId: {}, EventType: {}, Error: {}", 
                    redemptionId, customerId, eventType, e.getMessage(), e);
            throw new RuntimeException("Rewards redeemed event processing failed", e);
        }
    }
}
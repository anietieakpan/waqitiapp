package com.waqiti.rewards.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.rewards.service.LoyaltyPointsService;
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
public class LoyaltyPointsEventsConsumer {
    
    private final LoyaltyPointsService loyaltyPointsService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"loyalty-points-events", "points-expired", "points-transferred"},
        groupId = "rewards-service-loyalty-points-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleLoyaltyPointsEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID pointsId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            pointsId = UUID.fromString((String) event.get("pointsId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            BigDecimal pointsAmount = new BigDecimal(event.get("pointsAmount").toString());
            LocalDateTime eventDate = LocalDateTime.parse((String) event.get("eventDate"));
            String pointsType = (String) event.getOrDefault("pointsType", "STANDARD");
            LocalDateTime expirationDate = event.containsKey("expirationDate") ? 
                    LocalDateTime.parse((String) event.get("expirationDate")) : null;
            UUID transferToCustomerId = event.containsKey("transferToCustomerId") ? 
                    UUID.fromString((String) event.get("transferToCustomerId")) : null;
            
            log.info("Loyalty points event - PointsId: {}, CustomerId: {}, EventType: {}, Amount: {}", 
                    pointsId, customerId, eventType, pointsAmount);
            
            switch (eventType) {
                case "POINTS_EXPIRED" -> loyaltyPointsService.processPointsExpired(pointsId, 
                        customerId, pointsAmount, eventDate, expirationDate);
                case "POINTS_TRANSFERRED" -> loyaltyPointsService.processPointsTransferred(pointsId, 
                        customerId, transferToCustomerId, pointsAmount, eventDate);
                case "POINTS_ADJUSTED" -> loyaltyPointsService.processPointsAdjusted(pointsId, 
                        customerId, pointsAmount, eventDate, pointsType);
                default -> log.warn("Unknown loyalty points event type: {}", eventType);
            }
            
            auditService.auditFinancialEvent(
                    "LOYALTY_POINTS_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Loyalty points event %s - Amount: %s", eventType, pointsAmount),
                    Map.of(
                            "pointsId", pointsId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "pointsAmount", pointsAmount.toString(),
                            "pointsType", pointsType,
                            "transferToCustomerId", transferToCustomerId != null ? transferToCustomerId.toString() : "NONE"
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Loyalty points event processing failed - PointsId: {}, CustomerId: {}, Error: {}", 
                    pointsId, customerId, e.getMessage(), e);
            throw new RuntimeException("Loyalty points event processing failed", e);
        }
    }
}
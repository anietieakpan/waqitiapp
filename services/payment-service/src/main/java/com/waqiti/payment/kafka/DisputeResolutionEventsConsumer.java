package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.payment.service.DisputeResolutionService;
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
public class DisputeResolutionEventsConsumer {
    
    private final DisputeResolutionService disputeResolutionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"dispute-resolution-events", "dispute-resolved", "dispute-settled"},
        groupId = "payment-service-dispute-resolution-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleDisputeResolutionEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("DISPUTE RESOLUTION: Processing dispute resolution event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        UUID resolutionId = null;
        UUID disputeId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            resolutionId = UUID.fromString((String) event.get("resolutionId"));
            disputeId = UUID.fromString((String) event.get("disputeId"));
            UUID transactionId = UUID.fromString((String) event.get("transactionId"));
            UUID customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String resolutionStatus = (String) event.get("resolutionStatus");
            String resolutionType = (String) event.get("resolutionType");
            BigDecimal resolutionAmount = new BigDecimal(event.get("resolutionAmount").toString());
            String currency = (String) event.get("currency");
            LocalDateTime resolutionDate = LocalDateTime.parse((String) event.get("resolutionDate"));
            String resolutionReason = (String) event.get("resolutionReason");
            String resolutionMethod = (String) event.getOrDefault("resolutionMethod", "REFUND");
            UUID resolvedBy = event.containsKey("resolvedBy") ? 
                    UUID.fromString((String) event.get("resolvedBy")) : null;
            
            log.info("Dispute resolution event - ResolutionId: {}, DisputeId: {}, EventType: {}, Status: {}, Amount: {} {}", 
                    resolutionId, disputeId, eventType, resolutionStatus, resolutionAmount, currency);
            
            switch (eventType) {
                case "DISPUTE_RESOLVED" -> disputeResolutionService.processDisputeResolved(resolutionId, 
                        disputeId, transactionId, customerId, resolutionType, resolutionAmount, currency, 
                        resolutionDate, resolutionReason, resolutionMethod, resolvedBy);
                
                case "DISPUTE_SETTLED" -> disputeResolutionService.processDisputeSettled(resolutionId, 
                        disputeId, transactionId, customerId, resolutionAmount, currency, resolutionDate, 
                        resolutionMethod);
                
                case "RESOLUTION_REVERSED" -> disputeResolutionService.processResolutionReversed(
                        resolutionId, disputeId, transactionId, customerId, resolutionAmount, currency, 
                        resolutionReason);
                
                default -> log.warn("Unknown resolution event type: {}", eventType);
            }
            
            auditService.auditFinancialEvent(
                    "DISPUTE_RESOLUTION_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Dispute resolution event %s - Type: %s, Amount: %s %s", 
                            eventType, resolutionType, resolutionAmount, currency),
                    Map.of(
                            "resolutionId", resolutionId.toString(),
                            "disputeId", disputeId.toString(),
                            "transactionId", transactionId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "resolutionType", resolutionType,
                            "resolutionAmount", resolutionAmount.toString(),
                            "currency", currency,
                            "resolutionReason", resolutionReason
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Dispute resolution event processing failed - ResolutionId: {}, DisputeId: {}, EventType: {}, Error: {}", 
                    resolutionId, disputeId, eventType, e.getMessage(), e);
            throw new RuntimeException("Dispute resolution event processing failed", e);
        }
    }
}
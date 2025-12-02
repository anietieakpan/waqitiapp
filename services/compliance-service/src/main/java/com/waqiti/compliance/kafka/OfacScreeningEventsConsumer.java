package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.compliance.service.OfacScreeningService;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OfacScreeningEventsConsumer {
    
    private final OfacScreeningService ofacScreeningService;
    private final UniversalDLQHandler universalDLQHandler;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"ofac-screening-events", "ofac-match-found", "ofac-screening-completed"},
        groupId = "compliance-service-ofac-screening-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000)
    )
    @Transactional
    public void handleOfacScreeningEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID screeningId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            screeningId = UUID.fromString((String) event.get("screeningId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String screeningType = (String) event.get("screeningType");
            String screeningResult = (String) event.get("screeningResult");
            LocalDateTime screeningDate = LocalDateTime.parse((String) event.get("screeningDate"));
            String entityName = (String) event.get("entityName");
            String matchLevel = (String) event.getOrDefault("matchLevel", "NONE");
            String listName = (String) event.getOrDefault("listName", "");
            Boolean isMatch = (Boolean) event.getOrDefault("isMatch", false);
            String reviewStatus = (String) event.getOrDefault("reviewStatus", "PENDING");
            
            log.warn("OFAC screening event - ScreeningId: {}, CustomerId: {}, Result: {}, Match: {}, Level: {}", 
                    screeningId, customerId, screeningResult, isMatch, matchLevel);
            
            ofacScreeningService.processOfacScreening(screeningId, customerId, eventType, 
                    screeningType, screeningResult, screeningDate, entityName, matchLevel, 
                    listName, isMatch, reviewStatus);
            
            auditService.auditFinancialEvent(
                    "OFAC_SCREENING_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("OFAC screening %s - Result: %s, Match: %s, Level: %s, Entity: %s", 
                            eventType, screeningResult, isMatch, matchLevel, entityName),
                    Map.of(
                            "screeningId", screeningId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "screeningType", screeningType,
                            "screeningResult", screeningResult,
                            "entityName", entityName,
                            "matchLevel", matchLevel,
                            "listName", listName,
                            "isMatch", isMatch.toString(),
                            "reviewStatus", reviewStatus
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("OFAC screening event processing failed - ScreeningId: {}, CustomerId: {}, Error: {}",
                    screeningId, customerId, e.getMessage(), e);

            // Send to DLQ for retry/parking
            try {
                org.apache.kafka.clients.consumer.ConsumerRecord<String, String> consumerRecord =
                    new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        topic, partition, offset, String.valueOf(screeningId), eventJson);
                universalDLQHandler.handleFailedMessage(consumerRecord, e);
            } catch (Exception dlqEx) {
                log.error("CRITICAL: Failed to send OFAC screening to DLQ: {}", screeningId, dlqEx);
            }

            throw new RuntimeException("OFAC screening event processing failed", e);
        }
    }
}
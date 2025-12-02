package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.SessionManagementService;
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
public class SessionEventsConsumer {
    
    private final SessionManagementService sessionManagementService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"session-events", "session-created", "session-expired", "session-terminated"},
        groupId = "security-service-session-events-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleSessionEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID sessionId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            sessionId = UUID.fromString((String) event.get("sessionId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            LocalDateTime eventTime = LocalDateTime.parse((String) event.get("eventTime"));
            String sourceIp = (String) event.get("sourceIp");
            String userAgent = (String) event.get("userAgent");
            String deviceId = (String) event.getOrDefault("deviceId", "");
            Integer sessionDuration = event.containsKey("sessionDuration") ? 
                    (Integer) event.get("sessionDuration") : 0;
            String terminationReason = (String) event.getOrDefault("terminationReason", "");
            
            log.info("Session event - SessionId: {}, CustomerId: {}, EventType: {}, IP: {}, Duration: {}s", 
                    sessionId, customerId, eventType, sourceIp, sessionDuration);
            
            sessionManagementService.processSessionEvent(sessionId, customerId, eventType, 
                    eventTime, sourceIp, userAgent, deviceId, sessionDuration, terminationReason);
            
            auditService.auditFinancialEvent(
                    "SESSION_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Session event %s - IP: %s, Duration: %ds", eventType, sourceIp, sessionDuration),
                    Map.of(
                            "sessionId", sessionId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "sourceIp", sourceIp,
                            "userAgent", userAgent,
                            "deviceId", deviceId,
                            "sessionDuration", sessionDuration.toString(),
                            "terminationReason", terminationReason
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Session event processing failed - SessionId: {}, CustomerId: {}, Error: {}", 
                    sessionId, customerId, e.getMessage(), e);
            throw new RuntimeException("Session event processing failed", e);
        }
    }
}
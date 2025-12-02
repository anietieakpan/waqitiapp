package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.PasswordChangeService;
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
public class PasswordChangeEventsConsumer {
    
    private final PasswordChangeService passwordChangeService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"password-change-events", "password-changed", "password-reset"},
        groupId = "security-service-password-change-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handlePasswordChangeEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID eventId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            eventId = UUID.fromString((String) event.get("eventId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String changeType = (String) event.get("changeType");
            LocalDateTime changeDate = LocalDateTime.parse((String) event.get("changeDate"));
            String sourceIp = (String) event.get("sourceIp");
            String userAgent = (String) event.get("userAgent");
            String changeReason = (String) event.getOrDefault("changeReason", "");
            Boolean wasExpired = (Boolean) event.getOrDefault("wasExpired", false);
            Boolean isForced = (Boolean) event.getOrDefault("isForced", false);
            
            log.info("Password change event - EventId: {}, CustomerId: {}, Type: {}, ChangeType: {}, IP: {}", 
                    eventId, customerId, eventType, changeType, sourceIp);
            
            passwordChangeService.processPasswordChange(eventId, customerId, eventType, changeType, 
                    changeDate, sourceIp, userAgent, changeReason, wasExpired, isForced);
            
            auditService.auditFinancialEvent(
                    "PASSWORD_CHANGE_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Password change event %s - Type: %s, IP: %s", 
                            eventType, changeType, sourceIp),
                    Map.of(
                            "eventId", eventId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "changeType", changeType,
                            "sourceIp", sourceIp,
                            "userAgent", userAgent,
                            "changeReason", changeReason,
                            "wasExpired", wasExpired.toString(),
                            "isForced", isForced.toString()
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Password change event processing failed - EventId: {}, CustomerId: {}, Error: {}", 
                    eventId, customerId, e.getMessage(), e);
            throw new RuntimeException("Password change event processing failed", e);
        }
    }
}
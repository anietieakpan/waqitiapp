package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.LoginEventService;
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
public class LoginEventsConsumer {
    
    private final LoginEventService loginEventService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"login-events", "user-logged-in", "login-failed", "logout-events"},
        groupId = "security-service-login-events-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleLoginEvent(
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
            String loginStatus = (String) event.get("loginStatus");
            LocalDateTime eventTime = LocalDateTime.parse((String) event.get("eventTime"));
            String sourceIp = (String) event.get("sourceIp");
            String userAgent = (String) event.get("userAgent");
            String deviceId = (String) event.getOrDefault("deviceId", "");
            String location = (String) event.getOrDefault("location", "");
            String failureReason = (String) event.getOrDefault("failureReason", "");
            String sessionId = (String) event.getOrDefault("sessionId", "");
            
            log.info("Login event - EventId: {}, CustomerId: {}, Type: {}, Status: {}, IP: {}", 
                    eventId, customerId, eventType, loginStatus, sourceIp);
            
            loginEventService.processLoginEvent(eventId, customerId, eventType, loginStatus, 
                    eventTime, sourceIp, userAgent, deviceId, location, failureReason, sessionId);
            
            auditService.auditFinancialEvent(
                    "LOGIN_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Login event %s - Status: %s, IP: %s", eventType, loginStatus, sourceIp),
                    Map.of(
                            "eventId", eventId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "loginStatus", loginStatus,
                            "sourceIp", sourceIp,
                            "userAgent", userAgent,
                            "deviceId", deviceId,
                            "location", location,
                            "failureReason", failureReason,
                            "sessionId", sessionId
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Login event processing failed - EventId: {}, CustomerId: {}, Error: {}", 
                    eventId, customerId, e.getMessage(), e);
            throw new RuntimeException("Login event processing failed", e);
        }
    }
}
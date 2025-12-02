package com.waqiti.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.user.service.UserBehaviorService;
import com.waqiti.user.service.UserAnalyticsService;
import com.waqiti.user.service.UserSecurityService;
import com.waqiti.common.exception.UserProcessingException;
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

/**
 * Kafka Consumer for User Behavior Events
 * Handles user activity tracking, behavior analysis, and security monitoring
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserBehaviorEventsConsumer {
    
    private final UserBehaviorService behaviorService;
    private final UserAnalyticsService analyticsService;
    private final UserSecurityService securityService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"user-behavior-events", "user-activity-tracked", "behavior-pattern-detected", "security-anomaly-detected"},
        groupId = "user-service-behavior-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000)
    )
    @Transactional
    public void handleUserBehaviorEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID behaviorEventId = null;
        UUID userId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            behaviorEventId = UUID.fromString((String) event.get("behaviorEventId"));
            userId = UUID.fromString((String) event.get("userId"));
            eventType = (String) event.get("eventType");
            String activityType = (String) event.get("activityType");
            String sessionId = (String) event.get("sessionId");
            String deviceInfo = (String) event.get("deviceInfo");
            String ipAddress = (String) event.get("ipAddress");
            String location = (String) event.get("location");
            String userAgent = (String) event.get("userAgent");
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            log.info("Processing user behavior event - BehaviorEventId: {}, UserId: {}, Type: {}, Activity: {}", 
                    behaviorEventId, userId, eventType, activityType);
            
            switch (eventType) {
                case "USER_ACTIVITY_TRACKED":
                    behaviorService.trackUserActivity(behaviorEventId, userId, activityType,
                            sessionId, deviceInfo, ipAddress, location, timestamp);
                    break;
                case "BEHAVIOR_PATTERN_DETECTED":
                    behaviorService.processBehaviorPattern(behaviorEventId, userId, 
                            (String) event.get("patternType"), event, timestamp);
                    break;
                case "SECURITY_ANOMALY_DETECTED":
                    securityService.processSecurityAnomaly(behaviorEventId, userId,
                            (String) event.get("anomalyType"), event, timestamp);
                    break;
                default:
                    behaviorService.processGenericBehaviorEvent(behaviorEventId, eventType, event, timestamp);
            }
            
            analyticsService.updateUserAnalytics(userId, activityType, deviceInfo, location, timestamp);
            
            auditService.auditFinancialEvent(
                    "USER_BEHAVIOR_EVENT_PROCESSED",
                    userId.toString(),
                    String.format("User behavior event processed - Type: %s, Activity: %s", eventType, activityType),
                    Map.of(
                            "behaviorEventId", behaviorEventId.toString(),
                            "userId", userId.toString(),
                            "eventType", eventType,
                            "activityType", activityType,
                            "sessionId", sessionId,
                            "location", location != null ? location : "Unknown"
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed user behavior event - BehaviorEventId: {}, EventType: {}", 
                    behaviorEventId, eventType);
            
        } catch (Exception e) {
            log.error("User behavior event processing failed - BehaviorEventId: {}, UserId: {}, Error: {}", 
                    behaviorEventId, userId, e.getMessage(), e);
            throw new UserProcessingException("User behavior event processing failed", e);
        }
    }
}
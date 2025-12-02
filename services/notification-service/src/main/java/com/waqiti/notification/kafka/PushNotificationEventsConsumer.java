package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.notification.service.PushNotificationService;
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
public class PushNotificationEventsConsumer {
    
    private final PushNotificationService pushNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"push-notification-events", "notification-sent", "notification-failed"},
        groupId = "notification-service-push-notification-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handlePushNotificationEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID notificationId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            notificationId = UUID.fromString((String) event.get("notificationId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String notificationType = (String) event.get("notificationType");
            String title = (String) event.get("title");
            String message = (String) event.get("message");
            String status = (String) event.get("status");
            LocalDateTime sentDate = LocalDateTime.parse((String) event.get("sentDate"));
            String deviceToken = (String) event.getOrDefault("deviceToken", "");
            String priority = (String) event.getOrDefault("priority", "NORMAL");
            
            log.info("Push notification event - NotificationId: {}, CustomerId: {}, Type: {}, Status: {}", 
                    notificationId, customerId, notificationType, status);
            
            pushNotificationService.processPushNotification(notificationId, customerId, 
                    notificationType, title, message, status, sentDate, deviceToken, priority);
            
            auditService.auditFinancialEvent(
                    "PUSH_NOTIFICATION_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("Push notification %s - Type: %s, Status: %s", 
                            eventType, notificationType, status),
                    Map.of(
                            "notificationId", notificationId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "notificationType", notificationType,
                            "title", title,
                            "status", status,
                            "priority", priority
                    )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Push notification event processing failed - NotificationId: {}, CustomerId: {}, Error: {}", 
                    notificationId, customerId, e.getMessage(), e);
            throw new RuntimeException("Push notification event processing failed", e);
        }
    }
}
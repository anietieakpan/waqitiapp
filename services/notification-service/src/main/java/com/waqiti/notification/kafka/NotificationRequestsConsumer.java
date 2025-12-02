package com.waqiti.notification.kafka;

import com.waqiti.common.events.NotificationRequestEvent;
import com.waqiti.notification.domain.NotificationRequest;
import com.waqiti.notification.repository.NotificationRequestRepository;
import com.waqiti.notification.service.EmailDeliveryService;
import com.waqiti.notification.service.SmsDeliveryService;
import com.waqiti.notification.service.PushNotificationService;
import com.waqiti.notification.service.TemplateService;
import com.waqiti.notification.metrics.NotificationMetricsService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationRequestsConsumer {
    
    private final NotificationRequestRepository notificationRepository;
    private final EmailDeliveryService emailService;
    private final SmsDeliveryService smsService;
    private final PushNotificationService pushService;
    private final TemplateService templateService;
    private final NotificationMetricsService metricsService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    @KafkaListener(
        topics = {"notification-requests", "alert-notifications", "user-notifications"},
        groupId = "notification-requests-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "10"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 15000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleNotificationRequest(
            @Payload NotificationRequestEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("notif-%s-p%d-o%d", 
            event.getNotificationId(), partition, offset);
        
        log.info("Processing notification request: id={}, userId={}, channel={}, type={}",
            event.getNotificationId(), event.getUserId(), event.getChannel(), event.getNotificationType());
        
        try {
            NotificationRequest notification = NotificationRequest.builder()
                .notificationId(event.getNotificationId())
                .userId(event.getUserId())
                .channel(event.getChannel())
                .notificationType(event.getNotificationType())
                .priority(event.getPriority())
                .subject(event.getSubject())
                .message(event.getMessage())
                .templateId(event.getTemplateId())
                .templateData(event.getTemplateData())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            notificationRepository.save(notification);
            
            String renderedMessage = renderMessage(event);
            notification.setRenderedMessage(renderedMessage);
            notificationRepository.save(notification);
            
            boolean delivered = false;
            switch (event.getChannel().toUpperCase()) {
                case "EMAIL":
                    delivered = deliverEmail(event, renderedMessage, correlationId);
                    break;
                    
                case "SMS":
                    delivered = deliverSms(event, renderedMessage, correlationId);
                    break;
                    
                case "PUSH":
                    delivered = deliverPush(event, renderedMessage, correlationId);
                    break;
                    
                case "IN_APP":
                    delivered = deliverInApp(event, renderedMessage, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown notification channel: {}", event.getChannel());
                    break;
            }
            
            if (delivered) {
                notification.setStatus("DELIVERED");
                notification.setDeliveredAt(LocalDateTime.now());
            } else {
                notification.setStatus("FAILED");
                notification.setFailedAt(LocalDateTime.now());
                
                if (notification.getRetryCount() < MAX_RETRY_ATTEMPTS) {
                    scheduleRetry(event, notification.getRetryCount() + 1, correlationId);
                }
            }
            notificationRepository.save(notification);
            
            metricsService.recordNotificationProcessed(event.getChannel(), event.getNotificationType(), delivered);
            
            auditService.logNotificationEvent("NOTIFICATION_PROCESSED", event.getNotificationId(),
                Map.of("userId", event.getUserId(), "channel", event.getChannel(),
                    "type", event.getNotificationType(), "delivered", delivered,
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process notification request: {}", e.getMessage(), e);
            kafkaTemplate.send("notification-requests-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private String renderMessage(NotificationRequestEvent event) {
        if (event.getTemplateId() != null && event.getTemplateData() != null) {
            return templateService.renderTemplate(event.getTemplateId(), event.getTemplateData());
        }
        return event.getMessage();
    }
    
    private boolean deliverEmail(NotificationRequestEvent event, String message, String correlationId) {
        try {
            emailService.sendEmail(
                event.getUserId(),
                event.getRecipientEmail(),
                event.getSubject(),
                message,
                event.getPriority()
            );
            
            metricsService.recordEmailSent(event.getNotificationType());
            
            log.info("Email notification delivered: id={}, userId={}", 
                event.getNotificationId(), event.getUserId());
            
            return true;
        } catch (Exception e) {
            log.error("Failed to deliver email: {}", e.getMessage(), e);
            metricsService.recordEmailFailed(event.getNotificationType());
            return false;
        }
    }
    
    private boolean deliverSms(NotificationRequestEvent event, String message, String correlationId) {
        try {
            smsService.sendSms(
                event.getUserId(),
                event.getRecipientPhone(),
                message,
                event.getPriority()
            );
            
            metricsService.recordSmsSent(event.getNotificationType());
            
            log.info("SMS notification delivered: id={}, userId={}", 
                event.getNotificationId(), event.getUserId());
            
            return true;
        } catch (Exception e) {
            log.error("Failed to deliver SMS: {}", e.getMessage(), e);
            metricsService.recordSmsFailed(event.getNotificationType());
            return false;
        }
    }
    
    private boolean deliverPush(NotificationRequestEvent event, String message, String correlationId) {
        try {
            pushService.sendPushNotification(
                event.getUserId(),
                event.getDeviceTokens(),
                event.getSubject(),
                message,
                event.getPriority()
            );
            
            metricsService.recordPushSent(event.getNotificationType());
            
            log.info("Push notification delivered: id={}, userId={}, deviceCount={}", 
                event.getNotificationId(), event.getUserId(), event.getDeviceTokens().size());
            
            return true;
        } catch (Exception e) {
            log.error("Failed to deliver push notification: {}", e.getMessage(), e);
            metricsService.recordPushFailed(event.getNotificationType());
            return false;
        }
    }
    
    private boolean deliverInApp(NotificationRequestEvent event, String message, String correlationId) {
        try {
            kafkaTemplate.send("in-app-notifications", Map.of(
                "notificationId", event.getNotificationId(),
                "userId", event.getUserId(),
                "subject", event.getSubject(),
                "message", message,
                "priority", event.getPriority(),
                "type", event.getNotificationType(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            
            metricsService.recordInAppSent(event.getNotificationType());
            
            log.info("In-app notification delivered: id={}, userId={}", 
                event.getNotificationId(), event.getUserId());
            
            return true;
        } catch (Exception e) {
            log.error("Failed to deliver in-app notification: {}", e.getMessage(), e);
            metricsService.recordInAppFailed(event.getNotificationType());
            return false;
        }
    }
    
    private void scheduleRetry(NotificationRequestEvent event, int retryCount, String correlationId) {
        long delayMs = (long) Math.pow(2, retryCount) * 5000;
        
        kafkaTemplate.send("notification-retry-queue", Map.of(
            "notificationId", event.getNotificationId(),
            "userId", event.getUserId(),
            "channel", event.getChannel(),
            "notificationType", event.getNotificationType(),
            "retryCount", retryCount,
            "delayMs", delayMs,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.warn("Notification retry scheduled: id={}, retryCount={}, delayMs={}", 
            event.getNotificationId(), retryCount, delayMs);
    }
}
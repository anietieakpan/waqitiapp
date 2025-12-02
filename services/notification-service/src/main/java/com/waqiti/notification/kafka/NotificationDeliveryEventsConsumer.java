package com.waqiti.notification.kafka;

import com.waqiti.common.events.NotificationDeliveryEvent;
import com.waqiti.notification.domain.NotificationDelivery;
import com.waqiti.notification.domain.DeliveryAttempt;
import com.waqiti.notification.repository.NotificationDeliveryRepository;
import com.waqiti.notification.repository.DeliveryAttemptRepository;
import com.waqiti.notification.service.NotificationRetryService;
import com.waqiti.notification.service.DeliveryTrackingService;
import com.waqiti.notification.metrics.NotificationMetricsService;
import com.waqiti.common.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationDeliveryEventsConsumer {
    
    private final NotificationDeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final NotificationRetryService retryService;
    private final DeliveryTrackingService trackingService;
    private final NotificationMetricsService metricsService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MAX_RETRY_ATTEMPTS = 5;
    
    @KafkaListener(
        topics = {"notification-delivery-events", "message-delivery-tracking", "notification-status-updates"},
        groupId = "notification-delivery-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleNotificationDeliveryEvent(
            @Payload NotificationDeliveryEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("delivery-%s-p%d-o%d", 
            event.getNotificationId(), partition, offset);
        
        log.info("Processing notification delivery event: notificationId={}, type={}, status={}", 
            event.getNotificationId(), event.getEventType(), event.getDeliveryStatus());
        
        try {
            switch (event.getEventType()) {
                case DELIVERY_INITIATED:
                    processDeliveryInitiated(event, correlationId);
                    break;
                case DELIVERY_ATTEMPTED:
                    processDeliveryAttempted(event, correlationId);
                    break;
                case DELIVERY_SUCCESSFUL:
                    processDeliverySuccessful(event, correlationId);
                    break;
                case DELIVERY_FAILED:
                    processDeliveryFailed(event, correlationId);
                    break;
                case DELIVERY_BOUNCED:
                    processDeliveryBounced(event, correlationId);
                    break;
                case DELIVERY_DELAYED:
                    processDeliveryDelayed(event, correlationId);
                    break;
                case RETRY_SCHEDULED:
                    processRetryScheduled(event, correlationId);
                    break;
                case MAX_RETRIES_EXCEEDED:
                    processMaxRetriesExceeded(event, correlationId);
                    break;
                case NOTIFICATION_OPENED:
                    processNotificationOpened(event, correlationId);
                    break;
                case NOTIFICATION_CLICKED:
                    processNotificationClicked(event, correlationId);
                    break;
                case NOTIFICATION_DISMISSED:
                    processNotificationDismissed(event, correlationId);
                    break;
                case UNSUBSCRIBED:
                    processUnsubscribed(event, correlationId);
                    break;
                default:
                    log.warn("Unknown delivery event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logNotificationEvent(
                "DELIVERY_EVENT_PROCESSED",
                event.getNotificationId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "status", event.getDeliveryStatus(),
                    "channel", event.getChannel(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process delivery event: {}", e.getMessage(), e);
            kafkaTemplate.send("notification-delivery-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processDeliveryInitiated(NotificationDeliveryEvent event, String correlationId) {
        log.info("Notification delivery initiated: notificationId={}, channel={}, recipient={}", 
            event.getNotificationId(), event.getChannel(), event.getRecipient());
        
        NotificationDelivery delivery = NotificationDelivery.builder()
            .id(UUID.randomUUID().toString())
            .notificationId(event.getNotificationId())
            .userId(event.getUserId())
            .channel(event.getChannel())
            .recipient(event.getRecipient())
            .messageType(event.getMessageType())
            .priority(event.getPriority())
            .status("INITIATED")
            .initiatedAt(LocalDateTime.now())
            .attemptCount(0)
            .correlationId(correlationId)
            .build();
        
        deliveryRepository.save(delivery);
        trackingService.trackDelivery(delivery.getId());
        
        metricsService.recordDeliveryInitiated(event.getChannel(), event.getPriority());
    }
    
    private void processDeliveryAttempted(NotificationDeliveryEvent event, String correlationId) {
        log.info("Notification delivery attempted: notificationId={}, attempt={}", 
            event.getNotificationId(), event.getAttemptNumber());
        
        NotificationDelivery delivery = deliveryRepository.findByNotificationId(event.getNotificationId())
            .orElseThrow();
        
        delivery.setAttemptCount(event.getAttemptNumber());
        delivery.setStatus("ATTEMPTING");
        delivery.setLastAttemptAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
        
        DeliveryAttempt attempt = DeliveryAttempt.builder()
            .id(UUID.randomUUID().toString())
            .deliveryId(delivery.getId())
            .attemptNumber(event.getAttemptNumber())
            .attemptedAt(LocalDateTime.now())
            .provider(event.getProvider())
            .providerMessageId(event.getProviderMessageId())
            .correlationId(correlationId)
            .build();
        
        attemptRepository.save(attempt);
        metricsService.recordDeliveryAttempt(event.getChannel(), event.getAttemptNumber());
    }
    
    private void processDeliverySuccessful(NotificationDeliveryEvent event, String correlationId) {
        log.info("Notification delivered successfully: notificationId={}, channel={}, latency={}ms", 
            event.getNotificationId(), event.getChannel(), event.getDeliveryLatencyMs());
        
        NotificationDelivery delivery = deliveryRepository.findByNotificationId(event.getNotificationId())
            .orElseThrow();
        
        delivery.setStatus("DELIVERED");
        delivery.setDeliveredAt(LocalDateTime.now());
        delivery.setProviderMessageId(event.getProviderMessageId());
        delivery.setDeliveryLatencyMs(event.getDeliveryLatencyMs());
        
        Duration totalDuration = Duration.between(delivery.getInitiatedAt(), delivery.getDeliveredAt());
        delivery.setTotalDurationMs(totalDuration.toMillis());
        
        deliveryRepository.save(delivery);
        
        DeliveryAttempt attempt = attemptRepository.findLatestByDeliveryId(delivery.getId())
            .orElseThrow();
        attempt.setStatus("SUCCESS");
        attempt.setCompletedAt(LocalDateTime.now());
        attempt.setProviderResponse(event.getProviderResponse());
        attemptRepository.save(attempt);
        
        metricsService.recordDeliverySuccess(
            event.getChannel(), 
            event.getDeliveryLatencyMs(),
            delivery.getAttemptCount()
        );
    }
    
    private void processDeliveryFailed(NotificationDeliveryEvent event, String correlationId) {
        log.warn("Notification delivery failed: notificationId={}, reason={}, attempt={}", 
            event.getNotificationId(), event.getFailureReason(), event.getAttemptNumber());
        
        NotificationDelivery delivery = deliveryRepository.findByNotificationId(event.getNotificationId())
            .orElseThrow();
        
        delivery.setStatus("FAILED");
        delivery.setFailureReason(event.getFailureReason());
        delivery.setFailedAt(LocalDateTime.now());
        
        DeliveryAttempt attempt = attemptRepository.findLatestByDeliveryId(delivery.getId())
            .orElseThrow();
        attempt.setStatus("FAILED");
        attempt.setFailureReason(event.getFailureReason());
        attempt.setErrorCode(event.getErrorCode());
        attempt.setProviderResponse(event.getProviderResponse());
        attempt.setCompletedAt(LocalDateTime.now());
        attemptRepository.save(attempt);
        
        if (delivery.getAttemptCount() < MAX_RETRY_ATTEMPTS && isRetryable(event.getErrorCode())) {
            delivery.setStatus("RETRY_PENDING");
            deliveryRepository.save(delivery);
            retryService.scheduleRetry(delivery.getId(), delivery.getAttemptCount() + 1);
        } else {
            delivery.setStatus("PERMANENTLY_FAILED");
            deliveryRepository.save(delivery);
        }
        
        metricsService.recordDeliveryFailure(
            event.getChannel(), 
            event.getFailureReason(),
            event.getErrorCode()
        );
    }
    
    private void processDeliveryBounced(NotificationDeliveryEvent event, String correlationId) {
        log.warn("Notification bounced: notificationId={}, bounceType={}, reason={}", 
            event.getNotificationId(), event.getBounceType(), event.getBounceReason());
        
        NotificationDelivery delivery = deliveryRepository.findByNotificationId(event.getNotificationId())
            .orElseThrow();
        
        delivery.setStatus("BOUNCED");
        delivery.setBounceType(event.getBounceType());
        delivery.setBounceReason(event.getBounceReason());
        delivery.setBouncedAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
        
        if ("HARD".equals(event.getBounceType())) {
            trackingService.markRecipientInvalid(event.getRecipient(), event.getChannel());
        } else if ("SOFT".equals(event.getBounceType())) {
            retryService.scheduleRetry(delivery.getId(), delivery.getAttemptCount() + 1);
        }
        
        metricsService.recordDeliveryBounce(event.getChannel(), event.getBounceType());
    }
    
    private void processDeliveryDelayed(NotificationDeliveryEvent event, String correlationId) {
        log.info("Notification delivery delayed: notificationId={}, reason={}", 
            event.getNotificationId(), event.getDelayReason());
        
        NotificationDelivery delivery = deliveryRepository.findByNotificationId(event.getNotificationId())
            .orElseThrow();
        
        delivery.setStatus("DELAYED");
        delivery.setDelayReason(event.getDelayReason());
        delivery.setDelayedAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
        
        metricsService.recordDeliveryDelay(event.getChannel(), event.getDelayReason());
    }
    
    private void processRetryScheduled(NotificationDeliveryEvent event, String correlationId) {
        log.info("Notification retry scheduled: notificationId={}, nextAttempt={}, delay={}s", 
            event.getNotificationId(), event.getNextAttemptNumber(), event.getRetryDelaySeconds());
        
        NotificationDelivery delivery = deliveryRepository.findByNotificationId(event.getNotificationId())
            .orElseThrow();
        
        delivery.setStatus("RETRY_SCHEDULED");
        delivery.setNextRetryAt(LocalDateTime.now().plusSeconds(event.getRetryDelaySeconds()));
        deliveryRepository.save(delivery);
        
        metricsService.recordRetryScheduled(event.getChannel(), event.getNextAttemptNumber());
    }
    
    private void processMaxRetriesExceeded(NotificationDeliveryEvent event, String correlationId) {
        log.error("Max retries exceeded: notificationId={}, attempts={}", 
            event.getNotificationId(), event.getAttemptNumber());
        
        NotificationDelivery delivery = deliveryRepository.findByNotificationId(event.getNotificationId())
            .orElseThrow();
        
        delivery.setStatus("MAX_RETRIES_EXCEEDED");
        delivery.setFinalFailureReason("Maximum retry attempts exceeded");
        deliveryRepository.save(delivery);
        
        trackingService.escalateFailure(delivery.getId());
        metricsService.recordMaxRetriesExceeded(event.getChannel());
    }
    
    private void processNotificationOpened(NotificationDeliveryEvent event, String correlationId) {
        log.info("Notification opened: notificationId={}, device={}", 
            event.getNotificationId(), event.getDeviceType());
        
        NotificationDelivery delivery = deliveryRepository.findByNotificationId(event.getNotificationId())
            .orElseThrow();
        
        delivery.setOpened(true);
        delivery.setOpenedAt(LocalDateTime.now());
        delivery.setDeviceType(event.getDeviceType());
        delivery.setUserAgent(event.getUserAgent());
        
        Duration timeToOpen = Duration.between(delivery.getDeliveredAt(), delivery.getOpenedAt());
        delivery.setTimeToOpenMs(timeToOpen.toMillis());
        
        deliveryRepository.save(delivery);
        
        metricsService.recordNotificationOpened(
            event.getChannel(), 
            event.getMessageType(),
            timeToOpen.toMillis()
        );
    }
    
    private void processNotificationClicked(NotificationDeliveryEvent event, String correlationId) {
        log.info("Notification clicked: notificationId={}, link={}", 
            event.getNotificationId(), event.getClickedLink());
        
        NotificationDelivery delivery = deliveryRepository.findByNotificationId(event.getNotificationId())
            .orElseThrow();
        
        delivery.setClicked(true);
        delivery.setClickedAt(LocalDateTime.now());
        delivery.setClickedLink(event.getClickedLink());
        
        if (delivery.getOpenedAt() != null) {
            Duration timeToClick = Duration.between(delivery.getOpenedAt(), delivery.getClickedAt());
            delivery.setTimeToClickMs(timeToClick.toMillis());
        }
        
        deliveryRepository.save(delivery);
        
        metricsService.recordNotificationClicked(
            event.getChannel(), 
            event.getMessageType(),
            event.getClickedLink()
        );
    }
    
    private void processNotificationDismissed(NotificationDeliveryEvent event, String correlationId) {
        log.info("Notification dismissed: notificationId={}", event.getNotificationId());
        
        NotificationDelivery delivery = deliveryRepository.findByNotificationId(event.getNotificationId())
            .orElseThrow();
        
        delivery.setDismissed(true);
        delivery.setDismissedAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
        
        metricsService.recordNotificationDismissed(event.getChannel());
    }
    
    private void processUnsubscribed(NotificationDeliveryEvent event, String correlationId) {
        log.info("User unsubscribed: userId={}, channel={}, reason={}", 
            event.getUserId(), event.getChannel(), event.getUnsubscribeReason());
        
        NotificationDelivery delivery = deliveryRepository.findByNotificationId(event.getNotificationId())
            .orElseThrow();
        
        delivery.setUnsubscribed(true);
        delivery.setUnsubscribedAt(LocalDateTime.now());
        delivery.setUnsubscribeReason(event.getUnsubscribeReason());
        deliveryRepository.save(delivery);
        
        trackingService.updateUnsubscribePreferences(
            event.getUserId(), 
            event.getChannel(),
            event.getMessageType()
        );
        
        metricsService.recordUnsubscribe(event.getChannel(), event.getUnsubscribeReason());
    }
    
    private boolean isRetryable(String errorCode) {
        return errorCode != null && (
            errorCode.startsWith("5") ||
            errorCode.equals("TIMEOUT") ||
            errorCode.equals("RATE_LIMIT") ||
            errorCode.equals("TEMPORARY_FAILURE")
        );
    }
}
package com.waqiti.notification.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.notification.NotificationRequestEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.notification.domain.NotificationRequest;
import com.waqiti.notification.domain.NotificationChannel;
import com.waqiti.notification.domain.NotificationPriority;
import com.waqiti.notification.domain.NotificationStatus;
import com.waqiti.notification.repository.NotificationRequestRepository;
import com.waqiti.notification.service.MultiChannelNotificationService;
import com.waqiti.notification.service.NotificationTemplateService;
import com.waqiti.notification.service.DeliveryTrackingService;
import com.waqiti.notification.service.UserPreferenceService;
import com.waqiti.common.exceptions.NotificationProcessingException;

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
import java.util.List;

/**
 * Production-grade consumer for notification request events.
 * Handles multi-channel notification delivery with:
 * - Intelligent channel selection based on user preferences
 * - Template-based message rendering
 * - Delivery tracking and retry mechanisms
 * - Priority-based routing and scheduling
 * - Comprehensive delivery analytics
 * - Regulatory compliance for notifications
 * 
 * Critical for customer communication and engagement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRequestsConsumer {

    private final NotificationRequestRepository requestRepository;
    private final MultiChannelNotificationService deliveryService;
    private final NotificationTemplateService templateService;
    private final DeliveryTrackingService trackingService;
    private final UserPreferenceService preferenceService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    @KafkaListener(
        topics = "notification-requests",
        groupId = "notification-service-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        include = {NotificationProcessingException.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handleNotificationRequest(
            @Payload NotificationRequestEvent requestEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "priority", required = false) String priority,
            Acknowledgment acknowledgment) {

        String eventId = requestEvent.getEventId() != null ? 
            requestEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing notification request: {} for user: {} with type: {}", 
                    eventId, requestEvent.getUserId(), requestEvent.getNotificationType());

            // Metrics tracking
            metricsService.incrementCounter("notification.request.processing.started",
                Map.of(
                    "type", requestEvent.getNotificationType(),
                    "priority", priority != null ? priority : "normal"
                ));

            // Idempotency check
            if (isNotificationRequestProcessed(requestEvent.getUserId(), eventId)) {
                log.info("Notification request {} already processed for user {}", 
                        eventId, requestEvent.getUserId());
                acknowledgment.acknowledge();
                return;
            }

            // Create notification request record
            NotificationRequest notificationRequest = createNotificationRequest(requestEvent, eventId, correlationId);

            // Get user notification preferences
            var userPreferences = preferenceService.getUserPreferences(requestEvent.getUserId());

            // Determine optimal delivery channels
            List<NotificationChannel> channels = determineDeliveryChannels(requestEvent, userPreferences);

            // Render notification content using templates
            var renderedContent = templateService.renderNotificationContent(requestEvent, userPreferences.getLanguage());

            // Update request with channels and content
            notificationRequest.setChannels(channels);
            notificationRequest.setRenderedContent(renderedContent);
            notificationRequest.setScheduledDeliveryTime(calculateDeliveryTime(requestEvent, userPreferences));

            // Save notification request
            NotificationRequest savedRequest = requestRepository.save(notificationRequest);

            // Process delivery for each channel
            processMultiChannelDelivery(savedRequest, requestEvent, userPreferences);

            // Start delivery tracking
            trackingService.startTracking(savedRequest);

            // Update analytics
            updateNotificationAnalytics(savedRequest, requestEvent);

            // Create audit trail
            createNotificationAuditLog(savedRequest, requestEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("notification.request.processing.success",
                Map.of(
                    "type", requestEvent.getNotificationType(),
                    "channels", String.valueOf(channels.size())
                ));

            log.info("Successfully processed notification request: {} for user: {} via {} channels", 
                    savedRequest.getId(), requestEvent.getUserId(), channels.size());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing notification request {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("notification.request.processing.error");
            
            // Create error audit log
            auditLogger.logError("NOTIFICATION_REQUEST_PROCESSING_ERROR", 
                "system", eventId, e.getMessage(),
                Map.of(
                    "userId", requestEvent.getUserId(),
                    "notificationType", requestEvent.getNotificationType(),
                    "correlationId", correlationId
                ));
            
            throw new NotificationProcessingException("Failed to process notification request: " + e.getMessage(), e);
        }
    }

    /**
     * High-priority notification processor for urgent messages
     */
    @KafkaListener(
        topics = "notification-requests-urgent",
        groupId = "notification-service-urgent-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleUrgentNotificationRequest(
            @Payload NotificationRequestEvent requestEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.warn("URGENT NOTIFICATION: Processing high-priority notification: {} for user: {}", 
                    requestEvent.getEventId(), requestEvent.getUserId());

            // Process with highest priority
            handleNotificationRequest(requestEvent, "notification-requests-urgent", correlationId, "URGENT", acknowledgment);

        } catch (Exception e) {
            log.error("URGENT: Failed to process urgent notification: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent blocking urgent queue
        }
    }

    private boolean isNotificationRequestProcessed(String userId, String eventId) {
        return requestRepository.existsByUserIdAndEventId(userId, eventId);
    }

    private NotificationRequest createNotificationRequest(NotificationRequestEvent event, String eventId, String correlationId) {
        return NotificationRequest.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .userId(event.getUserId())
            .notificationType(event.getNotificationType())
            .priority(NotificationPriority.valueOf(event.getPriority() != null ? event.getPriority().toUpperCase() : "NORMAL"))
            .status(NotificationStatus.PENDING)
            .subject(event.getSubject())
            .message(event.getMessage())
            .templateId(event.getTemplateId())
            .templateData(event.getTemplateData())
            .metadata(event.getMetadata())
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private List<NotificationChannel> determineDeliveryChannels(NotificationRequestEvent event, 
                                                               UserPreferenceService.UserPreferences preferences) {
        // Start with user preferences
        List<NotificationChannel> channels = preferences.getPreferredChannels(event.getNotificationType());
        
        // Override based on priority and compliance requirements
        if (event.getPriority() != null && event.getPriority().equals("URGENT")) {
            // Urgent notifications should go through multiple channels
            if (!channels.contains(NotificationChannel.SMS)) {
                channels.add(NotificationChannel.SMS);
            }
            if (!channels.contains(NotificationChannel.PUSH)) {
                channels.add(NotificationChannel.PUSH);
            }
        }
        
        // Regulatory notifications must include email
        if (isRegulatoryNotification(event.getNotificationType()) && !channels.contains(NotificationChannel.EMAIL)) {
            channels.add(NotificationChannel.EMAIL);
        }
        
        // Security notifications require immediate channels
        if (isSecurityNotification(event.getNotificationType())) {
            channels.add(NotificationChannel.SMS);
            channels.add(NotificationChannel.PUSH);
        }
        
        return channels;
    }

    private LocalDateTime calculateDeliveryTime(NotificationRequestEvent event, 
                                              UserPreferenceService.UserPreferences preferences) {
        // Immediate delivery for urgent notifications
        if (event.getPriority() != null && event.getPriority().equals("URGENT")) {
            return LocalDateTime.now();
        }
        
        // Respect user's quiet hours for non-urgent notifications
        LocalDateTime now = LocalDateTime.now();
        if (preferences.isInQuietHours(now) && !isUrgentNotificationType(event.getNotificationType())) {
            return preferences.getNextActiveHour();
        }
        
        // Scheduled delivery if specified
        if (event.getScheduledDeliveryTime() != null) {
            return event.getScheduledDeliveryTime();
        }
        
        return now;
    }

    private void processMultiChannelDelivery(NotificationRequest request, NotificationRequestEvent event,
                                           UserPreferenceService.UserPreferences preferences) {
        for (NotificationChannel channel : request.getChannels()) {
            try {
                // Deliver notification via channel
                var deliveryResult = deliveryService.deliver(request, channel, preferences);
                
                // Update delivery status
                request.addChannelDelivery(channel, deliveryResult);
                
                log.info("Notification {} delivered via {}: {}", 
                        request.getId(), channel, deliveryResult.getStatus());
                
                // Metrics per channel
                metricsService.incrementCounter("notification.delivery.channel",
                    Map.of(
                        "channel", channel.toString(),
                        "status", deliveryResult.getStatus().toString(),
                        "type", request.getNotificationType()
                    ));
                
            } catch (Exception e) {
                log.error("Failed to deliver notification {} via {}: {}", 
                        request.getId(), channel, e.getMessage());
                
                // Record delivery failure
                request.addChannelFailure(channel, e.getMessage());
                
                // Metrics for failures
                metricsService.incrementCounter("notification.delivery.failure",
                    Map.of("channel", channel.toString()));
            }
        }
        
        // Update overall status
        updateOverallDeliveryStatus(request);
        
        // Save updated request
        requestRepository.save(request);
    }

    private void updateOverallDeliveryStatus(NotificationRequest request) {
        var channelResults = request.getChannelDeliveries();
        
        if (channelResults.isEmpty()) {
            request.setStatus(NotificationStatus.FAILED);
            return;
        }
        
        boolean anySuccessful = channelResults.values().stream()
            .anyMatch(result -> result.getStatus().toString().equals("DELIVERED"));
            
        boolean allFailed = channelResults.values().stream()
            .allMatch(result -> result.getStatus().toString().equals("FAILED"));
        
        if (anySuccessful) {
            request.setStatus(NotificationStatus.DELIVERED);
        } else if (allFailed) {
            request.setStatus(NotificationStatus.FAILED);
        } else {
            request.setStatus(NotificationStatus.PARTIALLY_DELIVERED);
        }
        
        request.setDeliveredAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
    }

    private void updateNotificationAnalytics(NotificationRequest request, NotificationRequestEvent event) {
        try {
            // Record notification metrics
            metricsService.incrementCounter("notification.type.processed",
                Map.of(
                    "type", request.getNotificationType(),
                    "priority", request.getPriority().toString()
                ));
            
            // Record delivery time metrics
            if (request.getDeliveredAt() != null) {
                long deliveryTimeMs = java.time.Duration.between(request.getCreatedAt(), request.getDeliveredAt()).toMillis();
                metricsService.recordTimer("notification.delivery.duration", deliveryTimeMs,
                    Map.of(
                        "type", request.getNotificationType(),
                        "priority", request.getPriority().toString()
                    ));
            }
            
            // User engagement tracking
            metricsService.incrementCounter("notification.user.sent",
                Map.of("userId", request.getUserId().substring(0, 8) + "...")); // Anonymized
            
        } catch (Exception e) {
            log.error("Failed to update notification analytics for {}: {}", request.getId(), e.getMessage());
        }
    }

    private void createNotificationAuditLog(NotificationRequest request, NotificationRequestEvent event, String correlationId) {
        auditLogger.logNotificationEvent(
            "NOTIFICATION_SENT",
            request.getUserId(),
            request.getId(),
            request.getNotificationType(),
            "notification_processor",
            request.getStatus().toString().equals("DELIVERED"),
            Map.of(
                "notificationType", request.getNotificationType(),
                "channels", request.getChannels().toString(),
                "priority", request.getPriority().toString(),
                "deliveryStatus", request.getStatus().toString(),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private boolean isRegulatoryNotification(String notificationType) {
        return notificationType.contains("COMPLIANCE") || 
               notificationType.contains("REGULATORY") ||
               notificationType.contains("KYC") ||
               notificationType.contains("AML");
    }

    private boolean isSecurityNotification(String notificationType) {
        return notificationType.contains("SECURITY") || 
               notificationType.contains("FRAUD") ||
               notificationType.contains("LOGIN") ||
               notificationType.contains("SUSPICIOUS");
    }

    private boolean isUrgentNotificationType(String notificationType) {
        return isSecurityNotification(notificationType) || 
               notificationType.contains("URGENT") ||
               notificationType.contains("CRITICAL") ||
               notificationType.contains("EMERGENCY");
    }
}
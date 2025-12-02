package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.customer.service.CustomerNotificationService;
import com.waqiti.customer.service.NotificationPreferenceService;
import com.waqiti.customer.service.NotificationDeliveryService;
import com.waqiti.customer.service.NotificationTemplateService;
import com.waqiti.customer.service.CustomerEngagementService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.CustomerNotification;
import com.waqiti.customer.domain.NotificationType;
import com.waqiti.customer.domain.NotificationChannel;
import com.waqiti.customer.domain.NotificationPriority;
import com.waqiti.customer.domain.DeliveryStatus;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerNotificationsConsumer {

    private final CustomerNotificationService customerNotificationService;
    private final NotificationPreferenceService preferenceService;
    private final NotificationDeliveryService deliveryService;
    private final NotificationTemplateService templateService;
    private final CustomerEngagementService engagementService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("customer_notifications_processed_total")
            .description("Total number of successfully processed customer notification events")
            .register(meterRegistry);
        errorCounter = Counter.builder("customer_notifications_errors_total")
            .description("Total number of customer notification processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("customer_notifications_processing_duration")
            .description("Time taken to process customer notification events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"customer-notifications", "customer-notification-delivery", "customer-notification-status"},
        groupId = "customer-notifications-service-group",
        containerFactory = "customerNotificationKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "customer-notifications", fallbackMethod = "handleCustomerNotificationEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCustomerNotificationEvent(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String customerId = (String) eventData.get("customerId");
        String eventType = (String) eventData.get("eventType");
        String correlationId = String.format("notif-%s-p%d-o%d", customerId, partition, offset);
        String eventKey = String.format("%s-%s-%s", customerId, eventType, eventData.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Notification event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing customer notification event: customerId={}, type={}, priority={}",
                customerId, eventType, eventData.get("priority"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (eventType) {
                case "NOTIFICATION_TRIGGERED":
                    processNotificationTriggered(eventData, correlationId);
                    break;

                case "NOTIFICATION_SCHEDULED":
                    scheduleNotification(eventData, correlationId);
                    break;

                case "NOTIFICATION_SENT":
                    processNotificationSent(eventData, correlationId);
                    break;

                case "NOTIFICATION_DELIVERED":
                    processNotificationDelivered(eventData, correlationId);
                    break;

                case "NOTIFICATION_FAILED":
                    processNotificationFailed(eventData, correlationId);
                    break;

                case "NOTIFICATION_READ":
                    processNotificationRead(eventData, correlationId);
                    break;

                case "NOTIFICATION_CLICKED":
                    processNotificationClicked(eventData, correlationId);
                    break;

                case "NOTIFICATION_DISMISSED":
                    processNotificationDismissed(eventData, correlationId);
                    break;

                case "PREFERENCE_UPDATED":
                    processPreferenceUpdated(eventData, correlationId);
                    break;

                default:
                    log.warn("Unknown customer notification event type: {}", eventType);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logCustomerEvent("CUSTOMER_NOTIFICATION_EVENT_PROCESSED", customerId,
                Map.of("eventType", eventType, "channel", eventData.get("channel"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process customer notification event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("customer-notifications-fallback-events", Map.of(
                "originalEvent", eventData, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCustomerNotificationEventFallback(
            Map<String, Object> eventData,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String customerId = (String) eventData.get("customerId");
        String correlationId = String.format("notif-fallback-%s-p%d-o%d", customerId, partition, offset);

        log.error("Circuit breaker fallback triggered for customer notification: customerId={}, error={}",
            customerId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("customer-notifications-dlq", Map.of(
            "originalEvent", eventData,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Customer Notification Circuit Breaker Triggered",
                String.format("Customer %s notification failed: %s", customerId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCustomerNotificationEvent(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String customerId = (String) eventData.get("customerId");
        String correlationId = String.format("dlt-notif-%s-%d", customerId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Customer notification permanently failed: customerId={}, topic={}, error={}",
            customerId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logCustomerEvent("CUSTOMER_NOTIFICATION_DLT_EVENT", customerId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", eventData.get("eventType"), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Customer Notification Dead Letter Event",
                String.format("Customer %s notification sent to DLT: %s", customerId, exceptionMessage),
                Map.of("customerId", customerId, "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processNotificationTriggered(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String notificationType = (String) eventData.get("notificationType");
        String channel = (String) eventData.get("channel");
        Map<String, Object> templateData = (Map<String, Object>) eventData.get("templateData");

        // Check customer preferences for this notification type
        if (!preferenceService.isNotificationAllowed(customerId, notificationType, channel)) {
            log.info("Notification blocked by customer preferences: customerId={}, type={}, channel={}",
                customerId, notificationType, channel);
            return;
        }

        // Get customer details for personalization
        Customer customer = customerNotificationService.getCustomer(customerId);
        if (customer == null) {
            log.error("Customer not found: customerId={}", customerId);
            return;
        }

        // Prepare notification template
        String template = templateService.getTemplate(notificationType, channel);
        String personalizedContent = templateService.personalizeContent(template, customer, templateData);

        // Create notification record
        CustomerNotification notification = CustomerNotification.builder()
            .customerId(customerId)
            .notificationType(NotificationType.valueOf(notificationType))
            .channel(NotificationChannel.valueOf(channel))
            .title((String) eventData.get("title"))
            .content(personalizedContent)
            .templateData(templateData)
            .priority(NotificationPriority.valueOf((String) eventData.getOrDefault("priority", "MEDIUM")))
            .status(DeliveryStatus.TRIGGERED)
            .scheduledFor(eventData.containsKey("scheduledFor") ?
                LocalDateTime.parse((String) eventData.get("scheduledFor")) : LocalDateTime.now())
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();

        customerNotificationService.saveNotification(notification);

        // Send to delivery service
        kafkaTemplate.send("customer-notification-delivery", Map.of(
            "customerId", customerId,
            "notificationId", notification.getId(),
            "eventType", "NOTIFICATION_SCHEDULED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Update engagement tracking
        engagementService.recordNotificationTriggered(customerId, notificationType);

        log.info("Notification triggered: customerId={}, type={}, channel={}",
            customerId, notificationType, channel);
    }

    private void scheduleNotification(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String notificationId = (String) eventData.get("notificationId");

        CustomerNotification notification = customerNotificationService.getNotification(notificationId);
        if (notification == null) {
            log.error("Notification not found: notificationId={}", notificationId);
            return;
        }

        notification.setStatus(DeliveryStatus.SCHEDULED);
        notification.setScheduledAt(LocalDateTime.now());
        customerNotificationService.saveNotification(notification);

        // Determine optimal delivery time based on customer behavior
        LocalDateTime optimalTime = engagementService.getOptimalNotificationTime(customerId, notification.getChannel());

        if (optimalTime.isAfter(LocalDateTime.now())) {
            // Schedule for later delivery
            deliveryService.scheduleNotification(notificationId, optimalTime);
        } else {
            // Send immediately
            kafkaTemplate.send("customer-notification-delivery", Map.of(
                "customerId", customerId,
                "notificationId", notificationId,
                "eventType", "NOTIFICATION_SENT",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Notification scheduled: customerId={}, notificationId={}, deliveryTime={}",
            customerId, notificationId, optimalTime);
    }

    private void processNotificationSent(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String notificationId = (String) eventData.get("notificationId");

        CustomerNotification notification = customerNotificationService.getNotification(notificationId);
        if (notification == null) {
            log.error("Notification not found: notificationId={}", notificationId);
            return;
        }

        notification.setStatus(DeliveryStatus.SENT);
        notification.setSentAt(LocalDateTime.now());
        customerNotificationService.saveNotification(notification);

        // Track delivery metrics
        engagementService.recordNotificationSent(customerId, notification.getNotificationType().toString());

        log.info("Notification sent: customerId={}, notificationId={}", customerId, notificationId);
    }

    private void processNotificationDelivered(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String notificationId = (String) eventData.get("notificationId");

        CustomerNotification notification = customerNotificationService.getNotification(notificationId);
        if (notification == null) {
            log.error("Notification not found: notificationId={}", notificationId);
            return;
        }

        notification.setStatus(DeliveryStatus.DELIVERED);
        notification.setDeliveredAt(LocalDateTime.now());
        customerNotificationService.saveNotification(notification);

        // Update engagement score
        engagementService.updateEngagementScore(customerId, "NOTIFICATION_DELIVERED");

        log.info("Notification delivered: customerId={}, notificationId={}", customerId, notificationId);
    }

    private void processNotificationFailed(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String notificationId = (String) eventData.get("notificationId");
        String errorReason = (String) eventData.get("errorReason");

        CustomerNotification notification = customerNotificationService.getNotification(notificationId);
        if (notification == null) {
            log.error("Notification not found: notificationId={}", notificationId);
            return;
        }

        notification.setStatus(DeliveryStatus.FAILED);
        notification.setFailureReason(errorReason);
        notification.setFailedAt(LocalDateTime.now());
        customerNotificationService.saveNotification(notification);

        // Handle retry logic based on failure reason
        if (deliveryService.shouldRetryDelivery(errorReason)) {
            deliveryService.scheduleNotificationRetry(notificationId);
        } else {
            // Try alternative channel if available
            NotificationChannel alternativeChannel = preferenceService.getAlternativeChannel(
                customerId, notification.getChannel());
            if (alternativeChannel != null) {
                deliveryService.scheduleAlternativeChannelDelivery(notificationId, alternativeChannel);
            }
        }

        // Track failure metrics
        engagementService.recordNotificationFailed(customerId, notification.getNotificationType().toString(), errorReason);

        log.warn("Notification failed: customerId={}, notificationId={}, reason={}",
            customerId, notificationId, errorReason);
    }

    private void processNotificationRead(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String notificationId = (String) eventData.get("notificationId");

        CustomerNotification notification = customerNotificationService.getNotification(notificationId);
        if (notification != null) {
            notification.setReadAt(LocalDateTime.now());
            customerNotificationService.saveNotification(notification);
        }

        // Track engagement
        engagementService.recordNotificationRead(customerId, notificationId);
        engagementService.updateEngagementScore(customerId, "NOTIFICATION_READ");

        log.info("Notification read: customerId={}, notificationId={}", customerId, notificationId);
    }

    private void processNotificationClicked(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String notificationId = (String) eventData.get("notificationId");
        String clickedAction = (String) eventData.get("clickedAction");

        // Track high-value engagement
        engagementService.recordNotificationClicked(customerId, notificationId, clickedAction);
        engagementService.updateEngagementScore(customerId, "NOTIFICATION_CLICKED");

        // Process any action-specific logic
        if (clickedAction != null) {
            processNotificationAction(customerId, notificationId, clickedAction, correlationId);
        }

        log.info("Notification clicked: customerId={}, notificationId={}, action={}",
            customerId, notificationId, clickedAction);
    }

    private void processNotificationDismissed(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String notificationId = (String) eventData.get("notificationId");

        CustomerNotification notification = customerNotificationService.getNotification(notificationId);
        if (notification != null) {
            notification.setDismissedAt(LocalDateTime.now());
            customerNotificationService.saveNotification(notification);
        }

        // Track dismissal for engagement analysis
        engagementService.recordNotificationDismissed(customerId, notificationId);

        log.info("Notification dismissed: customerId={}, notificationId={}", customerId, notificationId);
    }

    private void processPreferenceUpdated(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String notificationType = (String) eventData.get("notificationType");
        String channel = (String) eventData.get("channel");
        Boolean enabled = (Boolean) eventData.get("enabled");

        // Update customer notification preferences
        preferenceService.updateNotificationPreference(customerId, notificationType, channel, enabled);

        // Audit the preference change
        auditService.logCustomerEvent("NOTIFICATION_PREFERENCE_UPDATED", customerId,
            Map.of("notificationType", notificationType, "channel", channel, "enabled", enabled,
                "correlationId", correlationId, "timestamp", Instant.now()));

        log.info("Notification preference updated: customerId={}, type={}, channel={}, enabled={}",
            customerId, notificationType, channel, enabled);
    }

    private void processNotificationAction(String customerId, String notificationId, String action, String correlationId) {
        // Handle specific notification actions like "VIEW_OFFER", "COMPLETE_PROFILE", etc.
        switch (action) {
            case "VIEW_OFFER":
                // Trigger offer view tracking
                kafkaTemplate.send("customer-offer-interactions", Map.of(
                    "customerId", customerId,
                    "notificationId", notificationId,
                    "action", "OFFER_VIEWED_FROM_NOTIFICATION",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
                break;

            case "COMPLETE_PROFILE":
                // Trigger profile completion workflow
                kafkaTemplate.send("customer-profile-actions", Map.of(
                    "customerId", customerId,
                    "action", "PROFILE_COMPLETION_INITIATED",
                    "source", "NOTIFICATION",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
                break;

            case "DOWNLOAD_APP":
                // Track app download referral
                kafkaTemplate.send("customer-app-referrals", Map.of(
                    "customerId", customerId,
                    "source", "NOTIFICATION",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
                break;

            default:
                log.info("Unhandled notification action: {}", action);
                break;
        }
    }
}
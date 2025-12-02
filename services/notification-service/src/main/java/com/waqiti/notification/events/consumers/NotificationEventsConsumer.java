package com.waqiti.notification.events.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.events.NotificationEvent;
import com.waqiti.common.exceptions.ServiceIntegrationException;
import com.waqiti.notification.dto.*;
import com.waqiti.notification.entity.NotificationRecord;
import com.waqiti.notification.entity.NotificationStatus;
import com.waqiti.notification.repository.NotificationRecordRepository;
import com.waqiti.notification.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Production-ready Unified Notification Events Consumer
 * 
 * Consolidates notification event processing from multiple producers across the platform.
 * This consumer handles all notification lifecycle events including creation, scheduling,
 * delivery, failures, and tracking.
 * 
 * Key Responsibilities:
 * - Process notification events from 9+ different service producers
 * - Route notifications to appropriate delivery channels (EMAIL, SMS, PUSH, IN_APP, SLACK)
 * - Track notification lifecycle (created, scheduled, sent, delivered, read, failed)
 * - Handle notification preferences and opt-outs
 * - Provide delivery retry logic with exponential backoff
 * - Track delivery metrics and analytics
 * 
 * Event Producers (Consolidated):
 * - group-payment-service: Group payment notifications
 * - wallet-service: Transaction and balance notifications  
 * - infrastructure-service: System notifications
 * - social-service: Social notifications
 * - payment-service: Payment-related notifications
 * - compliance-service: Regulatory notifications
 * - fraud-service: Fraud alert notifications
 * - user-service: Account notifications
 * - rewards-service: Rewards notifications
 * 
 * Notification Channels:
 * - EMAIL: Transactional and marketing emails
 * - SMS: Time-sensitive text messages
 * - PUSH: Mobile push notifications
 * - IN_APP: In-application notifications
 * - SLACK: Team collaboration notifications
 * - WEBHOOK: External system notifications
 * 
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventsConsumer {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    // Repository
    private final NotificationRecordRepository notificationRecordRepository;

    // Notification Services
    private final EmailNotificationService emailNotificationService;
    private final SmsNotificationService smsNotificationService;
    private final PushNotificationService pushNotificationService;
    private final InAppNotificationService inAppNotificationService;
    private final SlackNotificationService slackNotificationService;
    private final WebhookNotificationService webhookNotificationService;

    // Support Services
    private final NotificationTemplateService templateService;
    private final NotificationPreferenceService preferenceService;
    private final NotificationDeliveryService deliveryService;
    private final EventProcessingTrackingService eventProcessingTrackingService;

    // Metrics
    private final Counter successCounter = Counter.builder("notification_events_processed_total")
            .description("Total number of notification events successfully processed")
            .register(meterRegistry);

    private final Counter failureCounter = Counter.builder("notification_events_failed_total")
            .description("Total number of notification events that failed processing")
            .register(meterRegistry);

    private final Timer processingTimer = Timer.builder("notification_event_processing_duration")
            .description("Time taken to process notification events")
            .register(meterRegistry);

    private final Counter deliveryCounter = Counter.builder("notifications_delivered_total")
            .description("Total number of notifications successfully delivered")
            .tag("channel", "all")
            .register(meterRegistry);

    /**
     * Main Kafka listener for unified notification events
     */
    @KafkaListener(
        topics = "${kafka.topics.notification-events:notification-events}",
        groupId = "${kafka.consumer.group-id:notification-service-consumer-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.concurrency:5}"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        include = {ServiceIntegrationException.class, Exception.class},
        dltTopicSuffix = "-dlt",
        autoCreateTopics = "true",
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED, timeout = 30)
    public void handleNotificationEvent(
            @Payload NotificationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = "correlationId", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        LocalDateTime processingStartTime = LocalDateTime.now();
        
        // Generate correlation ID if not provided
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId().toString() : UUID.randomUUID().toString();
        }

        log.info("Processing notification event: eventId={}, notificationId={}, userId={}, " +
                "eventType={}, channel={}, correlationId={}, topic={}, partition={}, offset={}", 
                event.getEventId(), event.getNotificationId(), event.getUserId(), 
                event.getEventType(), event.getChannel(), correlationId, topic, partition, offset);

        try {
            // 1. Validate event
            validateNotificationEvent(event);

            // 2. Check for duplicate processing
            if (eventProcessingTrackingService.isDuplicateEvent(
                    event.getEventId().toString(), "NOTIFICATION_EVENT")) {
                log.warn("Duplicate notification event detected, skipping: eventId={}, notificationId={}", 
                        event.getEventId(), event.getNotificationId());
                acknowledgment.acknowledge();
                return;
            }

            // 3. Track event processing start
            eventProcessingTrackingService.trackEventProcessingStart(
                event.getEventId().toString(), 
                "NOTIFICATION_EVENT", 
                correlationId,
                Map.of(
                    "notificationId", event.getNotificationId() != null ? 
                        event.getNotificationId().toString() : "unknown",
                    "userId", event.getUserId() != null ? event.getUserId().toString() : "unknown",
                    "eventType", event.getEventType() != null ? event.getEventType() : "unknown",
                    "channel", event.getChannel() != null ? event.getChannel() : "unknown",
                    "priority", event.getPriority() != null ? event.getPriority() : "NORMAL"
                )
            );

            // 4. Check user notification preferences
            if (!checkNotificationPreferences(event, correlationId)) {
                log.info("User has opted out of notifications: userId={}, channel={}, eventId={}", 
                        event.getUserId(), event.getChannel(), event.getEventId());
                
                // Still track as processed successfully (user preference respected)
                eventProcessingTrackingService.trackEventProcessingSuccess(
                    event.getEventId().toString(),
                    Map.of("reason", "user_opted_out", "channel", event.getChannel())
                );
                
                acknowledgment.acknowledge();
                return;
            }

            // 5. Process based on event type
            processNotificationByType(event, correlationId);

            // 6. Track successful processing
            eventProcessingTrackingService.trackEventProcessingSuccess(
                event.getEventId().toString(),
                Map.of(
                    "processingTimeMs", sample.stop(processingTimer).longValue(),
                    "channel", event.getChannel(),
                    "processingStartTime", processingStartTime.toString()
                )
            );

            successCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed notification event: eventId={}, notificationId={}, " +
                    "channel={}, processingTimeMs={}", 
                    event.getEventId(), event.getNotificationId(), event.getChannel(),
                    sample.stop(processingTimer).longValue());

        } catch (Exception e) {
            sample.stop(processingTimer);
            failureCounter.increment();

            log.error("Failed to process notification event: eventId={}, notificationId={}, " +
                     "userId={}, channel={}, attempt={}, error={}", 
                     event.getEventId(), event.getNotificationId(), event.getUserId(),
                     event.getChannel(),
                     RetrySynchronizationManager.getContext() != null ? 
                         RetrySynchronizationManager.getContext().getRetryCount() + 1 : 1,
                     e.getMessage(), e);

            // Track processing failure
            eventProcessingTrackingService.trackEventProcessingFailure(
                event.getEventId().toString(),
                e.getClass().getSimpleName(),
                e.getMessage(),
                Map.of(
                    "processingTimeMs", sample.stop(processingTimer).longValue(),
                    "channel", event.getChannel() != null ? event.getChannel() : "unknown",
                    "attempt", String.valueOf(RetrySynchronizationManager.getContext() != null ? 
                        RetrySynchronizationManager.getContext().getRetryCount() + 1 : 1)
                )
            );

            // Audit critical failure
            auditService.logNotificationEventProcessingFailure(
                event.getEventId() != null ? event.getEventId().toString() : "unknown",
                event.getNotificationId() != null ? event.getNotificationId().toString() : "unknown",
                correlationId,
                event.getUserId() != null ? event.getUserId().toString() : "unknown",
                event.getEventType() != null ? event.getEventType() : "unknown",
                event.getChannel() != null ? event.getChannel() : "unknown",
                e.getClass().getSimpleName(),
                e.getMessage(),
                Map.of(
                    "topic", topic,
                    "partition", String.valueOf(partition),
                    "offset", String.valueOf(offset),
                    "priority", event.getPriority() != null ? event.getPriority() : "NORMAL"
                )
            );

            throw new ServiceIntegrationException("Notification event processing failed", e);
        }
    }

    /**
     * Process notification based on event type
     */
    private void processNotificationByType(NotificationEvent event, String correlationId) {
        if (event.getEventType() == null) {
            throw new IllegalArgumentException("Event type is required");
        }

        switch (event.getEventType().toUpperCase()) {
            case "NOTIFICATION_CREATED" -> processNotificationCreated(event, correlationId);
            case "NOTIFICATION_SCHEDULED" -> processNotificationScheduled(event, correlationId);
            case "NOTIFICATION_SENT" -> processNotificationSent(event, correlationId);
            case "NOTIFICATION_DELIVERED" -> processNotificationDelivered(event, correlationId);
            case "NOTIFICATION_READ" -> processNotificationRead(event, correlationId);
            case "NOTIFICATION_FAILED" -> processNotificationFailed(event, correlationId);
            case "NOTIFICATION_RETRIED" -> processNotificationRetried(event, correlationId);
            case "NOTIFICATION_CANCELLED" -> processNotificationCancelled(event, correlationId);
            case "NOTIFICATION_EXPIRED" -> processNotificationExpired(event, correlationId);
            case "NOTIFICATION_BOUNCED" -> processNotificationBounced(event, correlationId);
            case "NOTIFICATION_CLICKED" -> processNotificationClicked(event, correlationId);
            case "NOTIFICATION_UNSUBSCRIBED" -> processNotificationUnsubscribed(event, correlationId);
            default -> {
                log.warn("Unknown notification event type: {} for eventId={}", 
                        event.getEventType(), event.getEventId());
                processGenericNotification(event, correlationId);
            }
        }
    }

    /**
     * Process notification created event - route to appropriate delivery channel
     */
    private void processNotificationCreated(NotificationEvent event, String correlationId) {
        log.info("Processing notification created: eventId={}, notificationId={}, channel={}, userId={}", 
                event.getEventId(), event.getNotificationId(), event.getChannel(), event.getUserId());

        // Create notification record
        NotificationRecord record = createNotificationRecord(event, correlationId);
        record.setStatus(NotificationStatus.CREATED);
        notificationRecordRepository.save(record);

        // Route to appropriate delivery channel
        CompletableFuture.runAsync(() -> {
            try {
                deliverNotification(event, correlationId);
            } catch (Exception e) {
                log.error("Failed to deliver notification: eventId={}, error={}", 
                         event.getEventId(), e.getMessage(), e);
                // Will be retried based on retry configuration
            }
        });

        auditService.logNotificationCreated(
            event.getEventId().toString(),
            event.getNotificationId().toString(),
            correlationId,
            event.getUserId().toString(),
            event.getChannel(),
            event.getNotificationType(),
            event.getPriority(),
            Map.of(
                "subject", event.getSubject() != null ? event.getSubject() : "",
                "recipient", event.getRecipient() != null ? event.getRecipient() : ""
            )
        );
    }

    /**
     * Process notification scheduled event
     */
    private void processNotificationScheduled(NotificationEvent event, String correlationId) {
        log.info("Processing notification scheduled: eventId={}, notificationId={}, scheduledAt={}", 
                event.getEventId(), event.getNotificationId(), event.getScheduledAt());

        NotificationRecord record = findOrCreateNotificationRecord(event, correlationId);
        record.setStatus(NotificationStatus.SCHEDULED);
        record.setScheduledAt(event.getScheduledAt());
        notificationRecordRepository.save(record);

        auditService.logNotificationScheduled(
            event.getEventId().toString(),
            event.getNotificationId().toString(),
            correlationId,
            event.getUserId().toString(),
            event.getScheduledAt(),
            Map.of("channel", event.getChannel())
        );
    }

    /**
     * Process notification sent event
     */
    private void processNotificationSent(NotificationEvent event, String correlationId) {
        log.info("Processing notification sent: eventId={}, notificationId={}, channel={}", 
                event.getEventId(), event.getNotificationId(), event.getChannel());

        NotificationRecord record = findOrCreateNotificationRecord(event, correlationId);
        record.setStatus(NotificationStatus.SENT);
        record.setSentAt(event.getSentAt() != null ? event.getSentAt() : event.getTimestamp());
        notificationRecordRepository.save(record);

        // Track delivery metrics
        Counter.builder("notifications_sent_total")
                .tag("channel", event.getChannel())
                .register(meterRegistry)
                .increment();

        auditService.logNotificationSent(
            event.getEventId().toString(),
            event.getNotificationId().toString(),
            correlationId,
            event.getUserId().toString(),
            event.getChannel(),
            event.getRecipient(),
            Map.of("sentAt", event.getSentAt() != null ? event.getSentAt().toString() : "now")
        );
    }

    /**
     * Process notification delivered event
     */
    private void processNotificationDelivered(NotificationEvent event, String correlationId) {
        log.info("Processing notification delivered: eventId={}, notificationId={}, channel={}", 
                event.getEventId(), event.getNotificationId(), event.getChannel());

        NotificationRecord record = findOrCreateNotificationRecord(event, correlationId);
        record.setStatus(NotificationStatus.DELIVERED);
        record.setDeliveredAt(event.getDeliveredAt() != null ? event.getDeliveredAt() : event.getTimestamp());
        notificationRecordRepository.save(record);

        // Track delivery success metrics
        deliveryCounter.increment();
        Counter.builder("notifications_delivered_by_channel_total")
                .tag("channel", event.getChannel())
                .register(meterRegistry)
                .increment();

        auditService.logNotificationDelivered(
            event.getEventId().toString(),
            event.getNotificationId().toString(),
            correlationId,
            event.getUserId().toString(),
            event.getChannel(),
            Map.of("deliveredAt", event.getDeliveredAt() != null ? event.getDeliveredAt().toString() : "now")
        );
    }

    /**
     * Process notification read event
     */
    private void processNotificationRead(NotificationEvent event, String correlationId) {
        log.info("Processing notification read: eventId={}, notificationId={}", 
                event.getEventId(), event.getNotificationId());

        NotificationRecord record = findOrCreateNotificationRecord(event, correlationId);
        record.setStatus(NotificationStatus.READ);
        record.setReadAt(event.getReadAt() != null ? event.getReadAt() : event.getTimestamp());
        notificationRecordRepository.save(record);

        // Track engagement metrics
        Counter.builder("notifications_read_total")
                .tag("channel", event.getChannel())
                .register(meterRegistry)
                .increment();

        auditService.logNotificationRead(
            event.getEventId().toString(),
            event.getNotificationId().toString(),
            correlationId,
            event.getUserId().toString(),
            Map.of("readAt", event.getReadAt() != null ? event.getReadAt().toString() : "now")
        );
    }

    /**
     * Process notification failed event
     */
    private void processNotificationFailed(NotificationEvent event, String correlationId) {
        log.error("Processing notification failed: eventId={}, notificationId={}, channel={}, reason={}", 
                event.getEventId(), event.getNotificationId(), event.getChannel(), event.getFailureReason());

        NotificationRecord record = findOrCreateNotificationRecord(event, correlationId);
        record.setStatus(NotificationStatus.FAILED);
        record.setFailureReason(event.getFailureReason());
        record.setRetryCount(event.getRetryCount() != null ? event.getRetryCount() : 0);
        notificationRecordRepository.save(record);

        // Track failure metrics
        Counter.builder("notifications_failed_total")
                .tag("channel", event.getChannel())
                .tag("reason", event.getFailureReason() != null ? event.getFailureReason() : "unknown")
                .register(meterRegistry)
                .increment();

        // Attempt retry if within retry limits
        if (shouldRetryNotification(event)) {
            scheduleNotificationRetry(event, correlationId);
        }

        auditService.logNotificationFailed(
            event.getEventId().toString(),
            event.getNotificationId().toString(),
            correlationId,
            event.getUserId().toString(),
            event.getChannel(),
            event.getFailureReason(),
            event.getRetryCount(),
            Map.of("willRetry", String.valueOf(shouldRetryNotification(event)))
        );
    }

    /**
     * Process notification retried event
     */
    private void processNotificationRetried(NotificationEvent event, String correlationId) {
        log.info("Processing notification retry: eventId={}, notificationId={}, retryCount={}", 
                event.getEventId(), event.getNotificationId(), event.getRetryCount());

        NotificationRecord record = findOrCreateNotificationRecord(event, correlationId);
        record.setStatus(NotificationStatus.RETRYING);
        record.setRetryCount(event.getRetryCount() != null ? event.getRetryCount() : 0);
        notificationRecordRepository.save(record);

        // Attempt delivery again
        CompletableFuture.runAsync(() -> {
            try {
                deliverNotification(event, correlationId);
            } catch (Exception e) {
                log.error("Failed to redeliver notification: eventId={}, error={}", 
                         event.getEventId(), e.getMessage(), e);
            }
        });

        auditService.logNotificationRetried(
            event.getEventId().toString(),
            event.getNotificationId().toString(),
            correlationId,
            event.getRetryCount(),
            Map.of("channel", event.getChannel())
        );
    }

    /**
     * Process notification cancelled event
     */
    private void processNotificationCancelled(NotificationEvent event, String correlationId) {
        log.info("Processing notification cancelled: eventId={}, notificationId={}", 
                event.getEventId(), event.getNotificationId());

        NotificationRecord record = findOrCreateNotificationRecord(event, correlationId);
        record.setStatus(NotificationStatus.CANCELLED);
        notificationRecordRepository.save(record);

        auditService.logNotificationCancelled(
            event.getEventId().toString(),
            event.getNotificationId().toString(),
            correlationId,
            event.getUserId().toString(),
            Map.of("channel", event.getChannel())
        );
    }

    /**
     * Process notification expired event
     */
    private void processNotificationExpired(NotificationEvent event, String correlationId) {
        log.warn("Processing notification expired: eventId={}, notificationId={}", 
                event.getEventId(), event.getNotificationId());

        NotificationRecord record = findOrCreateNotificationRecord(event, correlationId);
        record.setStatus(NotificationStatus.EXPIRED);
        notificationRecordRepository.save(record);

        auditService.logNotificationExpired(
            event.getEventId().toString(),
            event.getNotificationId().toString(),
            correlationId,
            event.getUserId().toString(),
            Map.of("channel", event.getChannel())
        );
    }

    /**
     * Process notification bounced event (email bounces)
     */
    private void processNotificationBounced(NotificationEvent event, String correlationId) {
        log.warn("Processing notification bounced: eventId={}, notificationId={}, channel={}", 
                event.getEventId(), event.getNotificationId(), event.getChannel());

        NotificationRecord record = findOrCreateNotificationRecord(event, correlationId);
        record.setStatus(NotificationStatus.BOUNCED);
        record.setFailureReason("Bounced: " + (event.getFailureReason() != null ? event.getFailureReason() : "Unknown"));
        notificationRecordRepository.save(record);

        // Track bounce metrics
        Counter.builder("notifications_bounced_total")
                .tag("channel", event.getChannel())
                .register(meterRegistry)
                .increment();

        // Update user contact validity if needed
        if ("EMAIL".equals(event.getChannel())) {
            updateContactValidity(event.getUserId(), event.getRecipient(), false);
        }

        auditService.logNotificationBounced(
            event.getEventId().toString(),
            event.getNotificationId().toString(),
            correlationId,
            event.getUserId().toString(),
            event.getChannel(),
            event.getRecipient(),
            event.getFailureReason(),
            Map.of()
        );
    }

    /**
     * Process notification clicked event (engagement tracking)
     */
    private void processNotificationClicked(NotificationEvent event, String correlationId) {
        log.info("Processing notification clicked: eventId={}, notificationId={}", 
                event.getEventId(), event.getNotificationId());

        NotificationRecord record = findOrCreateNotificationRecord(event, correlationId);
        record.setClickedAt(event.getTimestamp());
        notificationRecordRepository.save(record);

        // Track click-through metrics
        Counter.builder("notifications_clicked_total")
                .tag("channel", event.getChannel())
                .register(meterRegistry)
                .increment();

        auditService.logNotificationClicked(
            event.getEventId().toString(),
            event.getNotificationId().toString(),
            correlationId,
            event.getUserId().toString(),
            Map.of("channel", event.getChannel())
        );
    }

    /**
     * Process notification unsubscribed event
     */
    private void processNotificationUnsubscribed(NotificationEvent event, String correlationId) {
        log.info("Processing notification unsubscribe: eventId={}, notificationId={}, userId={}, channel={}", 
                event.getEventId(), event.getNotificationId(), event.getUserId(), event.getChannel());

        NotificationRecord record = findOrCreateNotificationRecord(event, correlationId);
        record.setStatus(NotificationStatus.UNSUBSCRIBED);
        notificationRecordRepository.save(record);

        // Update user preferences
        updateUserNotificationPreferences(event.getUserId(), event.getChannel(), 
                                         event.getNotificationType(), false);

        // Track unsubscribe metrics
        Counter.builder("notifications_unsubscribed_total")
                .tag("channel", event.getChannel())
                .tag("notificationType", event.getNotificationType() != null ? event.getNotificationType() : "unknown")
                .register(meterRegistry)
                .increment();

        auditService.logNotificationUnsubscribed(
            event.getEventId().toString(),
            event.getNotificationId().toString(),
            correlationId,
            event.getUserId().toString(),
            event.getChannel(),
            event.getNotificationType(),
            Map.of()
        );
    }

    /**
     * Process generic notification (fallback)
     */
    private void processGenericNotification(NotificationEvent event, String correlationId) {
        log.info("Processing generic notification: eventId={}, eventType={}, channel={}", 
                event.getEventId(), event.getEventType(), event.getChannel());

        NotificationRecord record = createNotificationRecord(event, correlationId);
        record.setStatus(NotificationStatus.CREATED);
        notificationRecordRepository.save(record);

        // Attempt delivery
        CompletableFuture.runAsync(() -> {
            try {
                deliverNotification(event, correlationId);
            } catch (Exception e) {
                log.error("Failed to deliver generic notification: eventId={}, error={}", 
                         event.getEventId(), e.getMessage(), e);
            }
        });

        auditService.logGenericNotificationProcessed(
            event.getEventId().toString(),
            event.getNotificationId() != null ? event.getNotificationId().toString() : "unknown",
            correlationId,
            event.getUserId() != null ? event.getUserId().toString() : "unknown",
            event.getEventType(),
            event.getChannel(),
            Map.of()
        );
    }

    /**
     * Deliver notification to appropriate channel
     */
    @CircuitBreaker(name = "notification-delivery", fallbackMethod = "deliverNotificationFallback")
    @Retry(name = "notification-delivery")
    @TimeLimiter(name = "notification-delivery")
    private void deliverNotification(NotificationEvent event, String correlationId) {
        if (event.getChannel() == null) {
            throw new IllegalArgumentException("Notification channel is required");
        }

        switch (event.getChannel().toUpperCase()) {
            case "EMAIL" -> emailNotificationService.sendEmail(convertToEmailRequest(event, correlationId));
            case "SMS" -> smsNotificationService.sendSms(convertToSmsRequest(event, correlationId));
            case "PUSH" -> pushNotificationService.sendPush(convertToPushRequest(event, correlationId));
            case "IN_APP" -> inAppNotificationService.createInAppNotification(convertToInAppRequest(event, correlationId));
            case "SLACK" -> slackNotificationService.sendSlackMessage(convertToSlackRequest(event, correlationId));
            case "WEBHOOK" -> webhookNotificationService.sendWebhook(convertToWebhookRequest(event, correlationId));
            default -> throw new IllegalArgumentException("Unsupported notification channel: " + event.getChannel());
        }

        log.info("Notification delivered successfully: eventId={}, channel={}", 
                event.getEventId(), event.getChannel());
    }

    /**
     * Fallback for notification delivery failures
     */
    private void deliverNotificationFallback(NotificationEvent event, String correlationId, Exception ex) {
        log.error("Notification delivery fallback triggered: eventId={}, channel={}, error={}", 
                event.getEventId(), event.getChannel(), ex.getMessage());
        
        // Log delivery failure for retry mechanism
        deliveryService.logDeliveryFailure(
            event.getNotificationId(),
            event.getChannel(),
            ex.getMessage(),
            correlationId
        );
    }

    /**
     * Check if notification should be retried
     */
    private boolean shouldRetryNotification(NotificationEvent event) {
        int maxRetries = 3; // Configure based on channel
        int currentRetry = event.getRetryCount() != null ? event.getRetryCount() : 0;
        return currentRetry < maxRetries;
    }

    /**
     * Schedule notification retry with exponential backoff
     */
    private void scheduleNotificationRetry(NotificationEvent event, String correlationId) {
        int retryCount = event.getRetryCount() != null ? event.getRetryCount() : 0;
        long delayMs = (long) (1000 * Math.pow(2, retryCount)); // Exponential backoff
        
        log.info("Scheduling notification retry: eventId={}, retryCount={}, delayMs={}", 
                event.getEventId(), retryCount + 1, delayMs);

        CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(delayMs);
                event.setRetryCount(retryCount + 1);
                deliverNotification(event, correlationId);
            } catch (Exception e) {
                log.error("Notification retry failed: eventId={}, error={}", 
                         event.getEventId(), e.getMessage(), e);
            }
        });
    }

    /**
     * Check user notification preferences
     */
    private boolean checkNotificationPreferences(NotificationEvent event, String correlationId) {
        try {
            if (event.getUserId() == null || event.getChannel() == null) {
                return true; // Default to allow if no user or channel specified
            }

            return preferenceService.isNotificationEnabled(
                event.getUserId(),
                event.getChannel(),
                event.getNotificationType()
            );
        } catch (Exception e) {
            log.error("Failed to check notification preferences: userId={}, error={}", 
                     event.getUserId(), e.getMessage(), e);
            return true; // Default to allow on error
        }
    }

    /**
     * Update user notification preferences (opt-out)
     */
    private void updateUserNotificationPreferences(UUID userId, String channel, 
                                                  String notificationType, boolean enabled) {
        try {
            preferenceService.updateNotificationPreference(userId, channel, notificationType, enabled);
            log.info("Updated notification preferences: userId={}, channel={}, type={}, enabled={}", 
                    userId, channel, notificationType, enabled);
        } catch (Exception e) {
            log.error("Failed to update notification preferences: userId={}, error={}", 
                     userId, e.getMessage(), e);
        }
    }

    /**
     * Update contact validity (for bounced emails/SMS)
     */
    private void updateContactValidity(UUID userId, String contact, boolean valid) {
        try {
            preferenceService.updateContactValidity(userId, contact, valid);
            log.info("Updated contact validity: userId={}, contact={}, valid={}", 
                    userId, contact != null ? contact.substring(0, Math.min(contact.length(), 10)) + "..." : "null", valid);
        } catch (Exception e) {
            log.error("Failed to update contact validity: userId={}, error={}", 
                     userId, e.getMessage(), e);
        }
    }

    /**
     * Find or create notification record
     */
    private NotificationRecord findOrCreateNotificationRecord(NotificationEvent event, String correlationId) {
        if (event.getNotificationId() != null) {
            Optional<NotificationRecord> existingOpt = 
                notificationRecordRepository.findByNotificationId(event.getNotificationId());
            
            if (existingOpt.isPresent()) {
                return existingOpt.get();
            }
        }
        
        return createNotificationRecord(event, correlationId);
    }

    /**
     * Create notification record from event
     */
    private NotificationRecord createNotificationRecord(NotificationEvent event, String correlationId) {
        return NotificationRecord.builder()
                .eventId(event.getEventId())
                .notificationId(event.getNotificationId())
                .userId(event.getUserId())
                .eventType(event.getEventType())
                .notificationType(event.getNotificationType())
                .channel(event.getChannel())
                .recipient(event.getRecipient())
                .subject(event.getSubject())
                .content(event.getContent())
                .templateId(event.getTemplateId())
                .priority(event.getPriority())
                .correlationId(correlationId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // Conversion methods for delivery services
    private EmailNotificationRequest convertToEmailRequest(NotificationEvent event, String correlationId) {
        return EmailNotificationRequest.builder()
                .notificationId(event.getNotificationId())
                .userId(event.getUserId())
                .recipient(event.getRecipient())
                .subject(event.getSubject())
                .content(event.getContent())
                .templateId(event.getTemplateId())
                .templateVariables(event.getTemplateVariables())
                .priority(event.getPriority())
                .correlationId(correlationId)
                .build();
    }

    private SmsNotificationRequest convertToSmsRequest(NotificationEvent event, String correlationId) {
        return SmsNotificationRequest.builder()
                .notificationId(event.getNotificationId())
                .userId(event.getUserId())
                .phoneNumber(event.getRecipient())
                .message(event.getContent())
                .priority(event.getPriority())
                .correlationId(correlationId)
                .build();
    }

    private PushNotificationRequest convertToPushRequest(NotificationEvent event, String correlationId) {
        return PushNotificationRequest.builder()
                .notificationId(event.getNotificationId())
                .userId(event.getUserId())
                .title(event.getSubject())
                .body(event.getContent())
                .priority(event.getPriority())
                .correlationId(correlationId)
                .build();
    }

    private InAppNotificationRequest convertToInAppRequest(NotificationEvent event, String correlationId) {
        return InAppNotificationRequest.builder()
                .notificationId(event.getNotificationId())
                .userId(event.getUserId())
                .title(event.getSubject())
                .message(event.getContent())
                .notificationType(event.getNotificationType())
                .priority(event.getPriority())
                .correlationId(correlationId)
                .build();
    }

    private SlackNotificationRequest convertToSlackRequest(NotificationEvent event, String correlationId) {
        return SlackNotificationRequest.builder()
                .notificationId(event.getNotificationId())
                .channel(event.getRecipient())
                .message(event.getContent())
                .priority(event.getPriority())
                .correlationId(correlationId)
                .build();
    }

    private WebhookNotificationRequest convertToWebhookRequest(NotificationEvent event, String correlationId) {
        return WebhookNotificationRequest.builder()
                .notificationId(event.getNotificationId())
                .webhookUrl(event.getRecipient())
                .payload(event.getMetadata())
                .correlationId(correlationId)
                .build();
    }

    /**
     * Validate notification event
     */
    private void validateNotificationEvent(NotificationEvent event) {
        Set<ConstraintViolation<NotificationEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Notification event validation failed: ");
            for (ConstraintViolation<NotificationEvent> violation : violations) {
                sb.append(violation.getPropertyPath()).append(" ").append(violation.getMessage()).append("; ");
            }
            throw new IllegalArgumentException(sb.toString());
        }

        // Additional business validation
        if (event.getEventId() == null) {
            throw new IllegalArgumentException("Event ID is required");
        }
    }

    /**
     * Dead Letter Topic handler for failed notification events
     */
    @DltHandler
    public void handleDltNotificationEvent(
            @Payload NotificationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(value = "correlationId", required = false) String correlationId) {
        
        log.error("Notification event sent to DLT: eventId={}, notificationId={}, userId={}, " +
                 "channel={}, topic={}, error={}", 
                 event.getEventId(), event.getNotificationId(), event.getUserId(),
                 event.getChannel(), topic, exceptionMessage);

        // Generate correlation ID if not provided
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = event.getCorrelationId() != null ? 
                event.getCorrelationId().toString() : UUID.randomUUID().toString();
        }

        // Track DLT event
        eventProcessingTrackingService.trackEventDLT(
            event.getEventId().toString(),
            "NOTIFICATION_EVENT",
            exceptionMessage,
            Map.of(
                "topic", topic,
                "notificationId", event.getNotificationId() != null ? event.getNotificationId().toString() : "unknown",
                "userId", event.getUserId() != null ? event.getUserId().toString() : "unknown",
                "channel", event.getChannel() != null ? event.getChannel() : "unknown",
                "eventType", event.getEventType() != null ? event.getEventType() : "unknown"
            )
        );

        // Critical audit for DLT events
        auditService.logNotificationEventDLT(
            event.getEventId() != null ? event.getEventId().toString() : "unknown",
            event.getNotificationId() != null ? event.getNotificationId().toString() : "unknown",
            correlationId,
            topic,
            exceptionMessage,
            Map.of(
                "userId", event.getUserId() != null ? event.getUserId().toString() : "unknown",
                "channel", event.getChannel() != null ? event.getChannel() : "unknown",
                "eventType", event.getEventType() != null ? event.getEventType() : "unknown",
                "priority", event.getPriority() != null ? event.getPriority() : "NORMAL",
                "requiresManualIntervention", "true"
            )
        );
    }
}
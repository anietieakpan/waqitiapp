package com.waqiti.kyc.kafka;

import com.waqiti.common.events.compliance.KycNotificationsEvent;
import com.waqiti.kyc.repository.KYCVerificationRepository;
import com.waqiti.kyc.repository.VerificationDocumentRepository;
import com.waqiti.kyc.service.KYCVerificationService;
import com.waqiti.kyc.service.NotificationService;
import com.waqiti.kyc.service.CacheService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService as CommonNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class KycNotificationsConsumer {

    private final KYCVerificationRepository verificationRepository;
    private final VerificationDocumentRepository documentRepository;
    private final KYCVerificationService verificationService;
    private final NotificationService notificationService;
    private final CacheService cacheService;
    private final AuditService auditService;
    private final CommonNotificationService commonNotificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("kyc_notifications_processed_total")
            .description("Total number of successfully processed KYC notification events")
            .register(meterRegistry);
        errorCounter = Counter.builder("kyc_notifications_errors_total")
            .description("Total number of KYC notification processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("kyc_notifications_processing_duration")
            .description("Time taken to process KYC notification events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"kyc-notifications", "kyc-notification-workflow", "kyc-alerts"},
        groupId = "kyc-notifications-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "kyc-notifications", fallbackMethod = "handleKycNotificationsEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleKycNotificationsEvent(
            @Payload KycNotificationsEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("kyc-notif-%s-p%d-o%d", event.getUserId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getUserId(), event.getNotificationType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing KYC notification: userId={}, notificationType={}, eventType={}",
                event.getUserId(), event.getNotificationType(), event.getEventType());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getNotificationType()) {
                case "VERIFICATION_STARTED":
                    sendVerificationStartedNotification(event, correlationId);
                    break;

                case "VERIFICATION_COMPLETED":
                    sendVerificationCompletedNotification(event, correlationId);
                    break;

                case "VERIFICATION_FAILED":
                    sendVerificationFailedNotification(event, correlationId);
                    break;

                case "DOCUMENT_REQUIRED":
                    sendDocumentRequiredNotification(event, correlationId);
                    break;

                case "DOCUMENT_APPROVED":
                    sendDocumentApprovedNotification(event, correlationId);
                    break;

                case "DOCUMENT_REJECTED":
                    sendDocumentRejectedNotification(event, correlationId);
                    break;

                case "REVIEW_REQUIRED":
                    sendReviewRequiredNotification(event, correlationId);
                    break;

                case "STATUS_UPDATE":
                    sendStatusUpdateNotification(event, correlationId);
                    break;

                case "COMPLIANCE_ALERT":
                    sendComplianceAlertNotification(event, correlationId);
                    break;

                case "EXPIRY_WARNING":
                    sendExpiryWarningNotification(event, correlationId);
                    break;

                default:
                    log.warn("Unknown KYC notification type: {}", event.getNotificationType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logUserEvent("KYC_NOTIFICATION_PROCESSED", event.getUserId(),
                Map.of("notificationType", event.getNotificationType(), "eventType", event.getEventType(),
                    "kycStatus", event.getKycStatus(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process KYC notification event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("kyc-notifications-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleKycNotificationsEventFallback(
            KycNotificationsEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("kyc-notif-fallback-%s-p%d-o%d", event.getUserId(), partition, offset);

        log.error("Circuit breaker fallback triggered for KYC notification: userId={}, error={}",
            event.getUserId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("kyc-notifications-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            commonNotificationService.sendOperationalAlert(
                "KYC Notification Circuit Breaker Triggered",
                String.format("KYC notification for user %s failed: %s", event.getUserId(), ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltKycNotificationsEvent(
            @Payload KycNotificationsEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-kyc-notif-%s-%d", event.getUserId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - KYC notification permanently failed: userId={}, topic={}, error={}",
            event.getUserId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logUserEvent("KYC_NOTIFICATION_DLT_EVENT", event.getUserId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "notificationType", event.getNotificationType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            commonNotificationService.sendCriticalAlert(
                "KYC Notification Dead Letter Event",
                String.format("KYC notification for user %s sent to DLT: %s", event.getUserId(), exceptionMessage),
                Map.of("userId", event.getUserId(), "topic", topic, "correlationId", correlationId)
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

    private void sendVerificationStartedNotification(KycNotificationsEvent event, String correlationId) {
        try {
            notificationService.sendUserNotification(
                event.getUserId(),
                "KYC Verification Started",
                "We've begun processing your identity verification. You'll be notified once complete.",
                event.getNotificationChannel(),
                correlationId
            );

            // Update cache with verification status
            cacheService.putKycStatus(event.getUserId(), "VERIFICATION_IN_PROGRESS",
                event.getKycLevel(), LocalDateTime.now());

            log.info("Sent verification started notification: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to send verification started notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void sendVerificationCompletedNotification(KycNotificationsEvent event, String correlationId) {
        try {
            notificationService.sendUserNotification(
                event.getUserId(),
                "KYC Verification Complete",
                String.format("Your identity verification is complete. KYC Level: %s", event.getKycLevel()),
                event.getNotificationChannel(),
                correlationId
            );

            // Update cache with new status
            cacheService.putKycStatus(event.getUserId(), "VERIFIED",
                event.getKycLevel(), LocalDateTime.now());

            log.info("Sent verification completed notification: userId={}, level={}",
                event.getUserId(), event.getKycLevel());
        } catch (Exception e) {
            log.error("Failed to send verification completed notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void sendVerificationFailedNotification(KycNotificationsEvent event, String correlationId) {
        try {
            notificationService.sendUserNotification(
                event.getUserId(),
                "KYC Verification Failed",
                "We were unable to verify your identity. Please review your documents and try again.",
                event.getNotificationChannel(),
                correlationId
            );

            // Update cache with failed status
            cacheService.putKycStatus(event.getUserId(), "FAILED",
                event.getKycLevel(), LocalDateTime.now());

            log.info("Sent verification failed notification: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to send verification failed notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void sendDocumentRequiredNotification(KycNotificationsEvent event, String correlationId) {
        try {
            notificationService.sendUserNotification(
                event.getUserId(),
                "Additional Documents Required",
                "Additional documents are needed to complete your verification. Please upload the required documents.",
                event.getNotificationChannel(),
                correlationId
            );

            log.info("Sent document required notification: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to send document required notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void sendDocumentApprovedNotification(KycNotificationsEvent event, String correlationId) {
        try {
            notificationService.sendUserNotification(
                event.getUserId(),
                "Document Approved",
                "Your submitted documents have been approved and are now being processed.",
                event.getNotificationChannel(),
                correlationId
            );

            log.info("Sent document approved notification: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to send document approved notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void sendDocumentRejectedNotification(KycNotificationsEvent event, String correlationId) {
        try {
            notificationService.sendUserNotification(
                event.getUserId(),
                "Document Rejected",
                "Some of your submitted documents were rejected. Please review the requirements and resubmit.",
                event.getNotificationChannel(),
                correlationId
            );

            log.info("Sent document rejected notification: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to send document rejected notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void sendReviewRequiredNotification(KycNotificationsEvent event, String correlationId) {
        try {
            notificationService.sendUserNotification(
                event.getUserId(),
                "Manual Review Required",
                "Your verification requires manual review. This may take 1-3 business days.",
                event.getNotificationChannel(),
                correlationId
            );

            // Update cache with review status
            cacheService.putKycStatus(event.getUserId(), "UNDER_REVIEW",
                event.getKycLevel(), LocalDateTime.now());

            log.info("Sent review required notification: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to send review required notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void sendStatusUpdateNotification(KycNotificationsEvent event, String correlationId) {
        try {
            notificationService.sendUserNotification(
                event.getUserId(),
                "KYC Status Update",
                String.format("Your KYC status has been updated to: %s", event.getKycStatus()),
                event.getNotificationChannel(),
                correlationId
            );

            // Update cache with new status
            cacheService.putKycStatus(event.getUserId(), event.getKycStatus(),
                event.getKycLevel(), LocalDateTime.now());

            log.info("Sent status update notification: userId={}, status={}",
                event.getUserId(), event.getKycStatus());
        } catch (Exception e) {
            log.error("Failed to send status update notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void sendComplianceAlertNotification(KycNotificationsEvent event, String correlationId) {
        try {
            // Send to compliance team instead of user
            commonNotificationService.sendComplianceAlert(
                "KYC Compliance Alert",
                String.format("Compliance issue detected for user %s: %s",
                    event.getUserId(), event.getMessage()),
                event.getPriority(),
                Map.of("userId", event.getUserId(), "verificationId", event.getVerificationId(),
                    "correlationId", correlationId)
            );

            log.info("Sent compliance alert notification: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to send compliance alert notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void sendExpiryWarningNotification(KycNotificationsEvent event, String correlationId) {
        try {
            notificationService.sendUserNotification(
                event.getUserId(),
                "KYC Verification Expiring Soon",
                "Your KYC verification will expire soon. Please update your documents to maintain account access.",
                event.getNotificationChannel(),
                correlationId
            );

            log.info("Sent expiry warning notification: userId={}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to send expiry warning notification: {}", e.getMessage(), e);
            throw e;
        }
    }
}
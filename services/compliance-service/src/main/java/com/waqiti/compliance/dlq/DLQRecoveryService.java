package com.waqiti.compliance.dlq;

import com.waqiti.common.audit.AuditService;
import com.waqiti.compliance.entity.DLQRecord;
import com.waqiti.compliance.enums.DLQRecoveryStatus;
import com.waqiti.compliance.repository.DLQRecordRepository;
import com.waqiti.compliance.service.ComplianceNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise-grade Dead Letter Queue (DLQ) Recovery Service.
 *
 * Provides comprehensive recovery mechanisms for failed Kafka messages including:
 * - Persistent DLQ record storage with full audit trail
 * - Intelligent retry with exponential backoff
 * - Manual review queue for compliance-critical failures
 * - Multi-channel alerting (Email, Slack, PagerDuty)
 * - Automatic escalation based on message priority
 * - Recovery metrics and reporting
 *
 * Regulatory Compliance:
 * - Ensures no SAR/CTR filing failures are lost
 * - Maintains complete audit trail for all DLQ messages
 * - Provides investigation capability for compliance officers
 *
 * @author Waqiti Compliance Engineering
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DLQRecoveryService {

    private final DLQRecordRepository dlqRecordRepository;
    private final ComplianceNotificationService notificationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DLQManualReviewQueue manualReviewQueue;
    private final DLQMetricsService metricsService;

    // Recovery configuration
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1 second
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long MAX_RETRY_DELAY_MS = 300000; // 5 minutes

    /**
     * Process a DLQ message with comprehensive recovery logic.
     *
     * @param topic Original Kafka topic
     * @param message Failed message payload
     * @param headers Message headers
     * @param exception Original exception that caused failure
     * @param priority Message priority (CRITICAL, HIGH, MEDIUM, LOW)
     * @return DLQ record with recovery status
     */
    @Transactional
    public DLQRecord processDLQMessage(
            String topic,
            Object message,
            Map<String, Object> headers,
            Exception exception,
            DLQPriority priority) {

        log.warn("Processing DLQ message from topic: {} with priority: {}", topic, priority);

        // 1. Create persistent DLQ record
        DLQRecord record = createDLQRecord(topic, message, headers, exception, priority);
        record = dlqRecordRepository.save(record);

        // 2. Log to audit system
        auditService.logComplianceEvent(
            "DLQ_MESSAGE_RECEIVED",
            String.format("DLQ message from topic %s: %s", topic, record.getId()),
            Map.of(
                "dlqRecordId", record.getId(),
                "topic", topic,
                "priority", priority.name(),
                "errorMessage", exception.getMessage()
            )
        );

        // 3. Update metrics
        metricsService.incrementDLQCounter(topic, priority);

        // 4. Determine recovery strategy based on priority and topic
        DLQRecoveryStrategy strategy = determineRecoveryStrategy(topic, priority, exception);

        // 5. Execute recovery strategy
        executeRecoveryStrategy(record, strategy);

        // 6. Send notifications based on priority
        sendNotifications(record, priority);

        return record;
    }

    /**
     * Create a comprehensive DLQ record with all relevant information.
     */
    private DLQRecord createDLQRecord(
            String topic,
            Object message,
            Map<String, Object> headers,
            Exception exception,
            DLQPriority priority) {

        DLQRecord record = new DLQRecord();
        record.setId(UUID.randomUUID().toString());
        record.setTopic(topic);
        record.setMessagePayload(serializeMessage(message));
        record.setHeaders(headers);
        record.setErrorMessage(exception.getMessage());
        record.setStackTrace(getStackTraceString(exception));
        record.setPriority(priority);
        record.setStatus(DLQRecoveryStatus.PENDING);
        record.setRetryCount(0);
        record.setCreatedAt(LocalDateTime.now());
        record.setLastAttemptAt(LocalDateTime.now());

        // Extract correlation ID if available
        if (headers.containsKey("correlationId")) {
            record.setCorrelationId((String) headers.get("correlationId"));
        }

        return record;
    }

    /**
     * Determine the appropriate recovery strategy based on topic and priority.
     */
    private DLQRecoveryStrategy determineRecoveryStrategy(
            String topic,
            DLQPriority priority,
            Exception exception) {

        // CRITICAL messages (SAR filing, transaction blocking, etc.)
        if (priority == DLQPriority.CRITICAL) {
            // For critical compliance events, require manual review
            if (isCriticalComplianceTopic(topic)) {
                return DLQRecoveryStrategy.MANUAL_REVIEW_REQUIRED;
            }
            // For other critical messages, immediate retry with escalation
            return DLQRecoveryStrategy.IMMEDIATE_RETRY_WITH_ESCALATION;
        }

        // HIGH priority messages
        if (priority == DLQPriority.HIGH) {
            return DLQRecoveryStrategy.EXPONENTIAL_BACKOFF_RETRY;
        }

        // Check if error is transient (network, timeout, etc.)
        if (isTransientError(exception)) {
            return DLQRecoveryStrategy.EXPONENTIAL_BACKOFF_RETRY;
        }

        // Check if error is permanent (deserialization, schema mismatch, etc.)
        if (isPermanentError(exception)) {
            return DLQRecoveryStrategy.MANUAL_REVIEW_REQUIRED;
        }

        // Default: Standard retry with backoff
        return DLQRecoveryStrategy.EXPONENTIAL_BACKOFF_RETRY;
    }

    /**
     * Execute the determined recovery strategy.
     */
    private void executeRecoveryStrategy(DLQRecord record, DLQRecoveryStrategy strategy) {
        switch (strategy) {
            case IMMEDIATE_RETRY_WITH_ESCALATION:
                scheduleImmediateRetry(record);
                escalateToCriticalQueue(record);
                break;

            case EXPONENTIAL_BACKOFF_RETRY:
                scheduleExponentialBackoffRetry(record);
                break;

            case MANUAL_REVIEW_REQUIRED:
                addToManualReviewQueue(record);
                break;

            case DISCARD:
                discardMessage(record);
                break;

            default:
                log.warn("Unknown recovery strategy: {}, defaulting to manual review", strategy);
                addToManualReviewQueue(record);
        }
    }

    /**
     * Schedule immediate retry for critical messages.
     */
    @Async
    public void scheduleImmediateRetry(DLQRecord record) {
        log.info("Scheduling immediate retry for DLQ record: {}", record.getId());

        try {
            // Wait a short delay to avoid immediate re-failure
            Thread.sleep(INITIAL_RETRY_DELAY_MS);

            // Attempt to republish to original topic
            republishMessage(record);

            // Update record
            record.setRetryCount(record.getRetryCount() + 1);
            record.setLastAttemptAt(LocalDateTime.now());
            record.setStatus(DLQRecoveryStatus.RETRY_SCHEDULED);
            dlqRecordRepository.save(record);

        } catch (Exception e) {
            log.error("Immediate retry failed for DLQ record: {}", record.getId(), e);
            handleRetryFailure(record, e);
        }
    }

    /**
     * Schedule retry with exponential backoff.
     */
    @Async
    public void scheduleExponentialBackoffRetry(DLQRecord record) {
        log.info("Scheduling exponential backoff retry for DLQ record: {}", record.getId());

        CompletableFuture.runAsync(() -> {
            int retryCount = record.getRetryCount();

            if (retryCount >= MAX_RETRY_ATTEMPTS) {
                log.warn("Max retry attempts reached for DLQ record: {}", record.getId());
                escalateToManualReview(record, "Max retry attempts exceeded");
                return;
            }

            try {
                // Calculate backoff delay: delay = initial * (multiplier ^ retryCount)
                long delayMs = calculateBackoffDelay(retryCount);
                log.info("Retry #{} scheduled in {}ms for DLQ record: {}",
                    retryCount + 1, delayMs, record.getId());

                Thread.sleep(delayMs);

                // Attempt to republish
                republishMessage(record);

                // Update record
                record.setRetryCount(retryCount + 1);
                record.setLastAttemptAt(LocalDateTime.now());
                record.setStatus(DLQRecoveryStatus.RETRY_IN_PROGRESS);
                dlqRecordRepository.save(record);

                // Log successful retry
                auditService.logComplianceEvent(
                    "DLQ_RETRY_SUCCESSFUL",
                    String.format("DLQ record %s successfully retried", record.getId()),
                    Map.of("dlqRecordId", record.getId(), "retryCount", retryCount + 1)
                );

            } catch (Exception e) {
                log.error("Exponential backoff retry failed for DLQ record: {}", record.getId(), e);
                handleRetryFailure(record, e);
            }
        });
    }

    /**
     * Calculate exponential backoff delay with cap.
     */
    private long calculateBackoffDelay(int retryCount) {
        long delay = (long) (INITIAL_RETRY_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, retryCount));
        return Math.min(delay, MAX_RETRY_DELAY_MS);
    }

    /**
     * Republish message to original topic.
     */
    private void republishMessage(DLQRecord record) {
        log.info("Republishing message to topic: {}", record.getTopic());

        Object message = deserializeMessage(record.getMessagePayload());

        // Add retry metadata to headers
        Map<String, Object> headers = new HashMap<>(record.getHeaders());
        headers.put("X-DLQ-Retry-Count", record.getRetryCount() + 1);
        headers.put("X-DLQ-Record-Id", record.getId());
        headers.put("X-DLQ-Original-Failure", record.getErrorMessage());

        kafkaTemplate.send(record.getTopic(), message);

        metricsService.incrementRetryCounter(record.getTopic(), true);
    }

    /**
     * Handle retry failure - update record and escalate if needed.
     */
    private void handleRetryFailure(DLQRecord record, Exception e) {
        record.setRetryCount(record.getRetryCount() + 1);
        record.setLastAttemptAt(LocalDateTime.now());
        record.setLastErrorMessage(e.getMessage());

        if (record.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
            record.setStatus(DLQRecoveryStatus.MAX_RETRIES_EXCEEDED);
            escalateToManualReview(record, "Max retries exceeded after failure");
        } else {
            record.setStatus(DLQRecoveryStatus.RETRY_FAILED);
            // Schedule next retry
            scheduleExponentialBackoffRetry(record);
        }

        dlqRecordRepository.save(record);
        metricsService.incrementRetryCounter(record.getTopic(), false);
    }

    /**
     * Add message to manual review queue for compliance officer investigation.
     */
    private void addToManualReviewQueue(DLQRecord record) {
        log.warn("Adding DLQ record to manual review queue: {}", record.getId());

        record.setStatus(DLQRecoveryStatus.MANUAL_REVIEW_REQUIRED);
        record.setReviewQueuedAt(LocalDateTime.now());
        dlqRecordRepository.save(record);

        // Add to manual review queue
        manualReviewQueue.add(record);

        // Create investigation case
        Map<String, Object> caseData = Map.of(
            "dlqRecordId", record.getId(),
            "topic", record.getTopic(),
            "priority", record.getPriority().name(),
            "errorMessage", record.getErrorMessage(),
            "retryCount", record.getRetryCount()
        );

        auditService.logComplianceEvent(
            "DLQ_MANUAL_REVIEW_REQUIRED",
            String.format("DLQ record %s requires manual review", record.getId()),
            caseData
        );

        metricsService.incrementManualReviewCounter(record.getTopic());
    }

    /**
     * Escalate critical DLQ record to manual review.
     */
    private void escalateToManualReview(DLQRecord record, String reason) {
        log.error("Escalating DLQ record {} to manual review: {}", record.getId(), reason);

        record.setStatus(DLQRecoveryStatus.ESCALATED);
        record.setEscalationReason(reason);
        record.setEscalatedAt(LocalDateTime.now());
        dlqRecordRepository.save(record);

        addToManualReviewQueue(record);

        // Send critical alert
        notificationService.sendCriticalAlert(
            "DLQ_ESCALATION",
            String.format("DLQ record %s escalated: %s", record.getId(), reason),
            Map.of(
                "dlqRecordId", record.getId(),
                "topic", record.getTopic(),
                "reason", reason
            )
        );
    }

    /**
     * Escalate to critical queue (highest priority).
     */
    private void escalateToCriticalQueue(DLQRecord record) {
        log.error("Escalating to CRITICAL queue: {}", record.getId());

        record.setPriority(DLQPriority.CRITICAL);
        dlqRecordRepository.save(record);

        // Send PagerDuty alert for critical compliance failures
        notificationService.sendPagerDutyAlert(
            "CRITICAL_DLQ_FAILURE",
            String.format("Critical compliance message failed: %s", record.getTopic()),
            Map.of(
                "dlqRecordId", record.getId(),
                "topic", record.getTopic(),
                "errorMessage", record.getErrorMessage()
            )
        );
    }

    /**
     * Send notifications based on message priority.
     */
    private void sendNotifications(DLQRecord record, DLQPriority priority) {
        switch (priority) {
            case CRITICAL:
                // PagerDuty alert for immediate response
                notificationService.sendPagerDutyAlert(
                    "CRITICAL_DLQ",
                    String.format("Critical DLQ message: %s", record.getTopic()),
                    Map.of("dlqRecordId", record.getId())
                );
                // Email to compliance team
                notificationService.sendComplianceEmail(
                    "Critical DLQ Message Requires Attention",
                    formatDLQEmailBody(record)
                );
                // Slack notification
                notificationService.sendSlackAlert(
                    "#compliance-critical",
                    String.format("🚨 CRITICAL DLQ: %s - Record ID: %s", record.getTopic(), record.getId())
                );
                break;

            case HIGH:
                // Email to compliance team
                notificationService.sendComplianceEmail(
                    "High Priority DLQ Message",
                    formatDLQEmailBody(record)
                );
                // Slack notification
                notificationService.sendSlackAlert(
                    "#compliance-alerts",
                    String.format("⚠️ HIGH PRIORITY DLQ: %s - Record ID: %s", record.getTopic(), record.getId())
                );
                break;

            case MEDIUM:
                // Slack notification only
                notificationService.sendSlackAlert(
                    "#compliance-notifications",
                    String.format("ℹ️ DLQ Message: %s - Record ID: %s", record.getTopic(), record.getId())
                );
                break;

            case LOW:
                // Log only, no immediate notification
                log.info("Low priority DLQ message logged: {}", record.getId());
                break;
        }
    }

    /**
     * Discard message (only for non-critical, unrecoverable messages).
     */
    private void discardMessage(DLQRecord record) {
        log.warn("Discarding DLQ record: {}", record.getId());

        record.setStatus(DLQRecoveryStatus.DISCARDED);
        record.setDiscardedAt(LocalDateTime.now());
        dlqRecordRepository.save(record);

        auditService.logComplianceEvent(
            "DLQ_MESSAGE_DISCARDED",
            String.format("DLQ record %s discarded", record.getId()),
            Map.of("dlqRecordId", record.getId(), "topic", record.getTopic())
        );
    }

    // Helper methods

    private boolean isCriticalComplianceTopic(String topic) {
        return topic.contains("SAR") ||
               topic.contains("CTR") ||
               topic.contains("sanctions") ||
               topic.contains("transaction-block") ||
               topic.contains("critical");
    }

    private boolean isTransientError(Exception exception) {
        String message = exception.getMessage().toLowerCase();
        return message.contains("timeout") ||
               message.contains("connection") ||
               message.contains("network") ||
               message.contains("unavailable") ||
               exception instanceof java.net.SocketTimeoutException ||
               exception instanceof java.io.IOException;
    }

    private boolean isPermanentError(Exception exception) {
        String message = exception.getMessage().toLowerCase();
        return message.contains("deserialization") ||
               message.contains("schema") ||
               message.contains("parse") ||
               message.contains("invalid format") ||
               exception instanceof org.apache.kafka.common.errors.SerializationException;
    }

    private String serializeMessage(Object message) {
        // Implementation depends on serialization format (JSON, Avro, etc.)
        try {
            if (message instanceof String) {
                return (String) message;
            }
            // Use Jackson or configured serializer
            return message.toString();
        } catch (Exception e) {
            log.error("Failed to serialize message", e);
            return "SERIALIZATION_FAILED: " + e.getMessage();
        }
    }

    private Object deserializeMessage(String payload) {
        // Implementation depends on serialization format
        return payload;
    }

    private String getStackTraceString(Exception exception) {
        StringBuilder sb = new StringBuilder();
        sb.append(exception.getClass().getName())
          .append(": ")
          .append(exception.getMessage())
          .append("\n");

        for (StackTraceElement element : exception.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        if (exception.getCause() != null) {
            sb.append("Caused by: ").append(getStackTraceString((Exception) exception.getCause()));
        }

        return sb.toString();
    }

    private String formatDLQEmailBody(DLQRecord record) {
        return String.format(
            "DLQ Message Details:\n\n" +
            "Record ID: %s\n" +
            "Topic: %s\n" +
            "Priority: %s\n" +
            "Status: %s\n" +
            "Error: %s\n" +
            "Retry Count: %d\n" +
            "Created: %s\n\n" +
            "Please review this message in the DLQ management dashboard.",
            record.getId(),
            record.getTopic(),
            record.getPriority(),
            record.getStatus(),
            record.getErrorMessage(),
            record.getRetryCount(),
            record.getCreatedAt()
        );
    }
}

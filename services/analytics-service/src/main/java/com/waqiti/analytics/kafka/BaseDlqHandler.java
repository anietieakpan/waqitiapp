package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.analytics.entity.DlqMessage;
import com.waqiti.analytics.repository.DlqMessageRepository;
import com.waqiti.analytics.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base Dead Letter Queue Handler
 *
 * Provides common infrastructure for all DLQ handlers including:
 * - Message persistence to database
 * - Retry logic with exponential backoff
 * - Correlation ID tracking
 * - Operations team alerting
 * - Manual review queue integration
 *
 * Subclasses must implement:
 * - handleDlqMessage(): Business logic for processing the specific message type
 * - getOriginalTopic(): Return the original topic name
 * - getDlqTopic(): Return the DLQ topic name
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@Slf4j
public abstract class BaseDlqHandler {

    @Autowired
    protected DlqMessageRepository dlqMessageRepository;

    @Autowired
    protected NotificationService notificationService;

    @Autowired
    protected ObjectMapper objectMapper;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int INITIAL_RETRY_DELAY_MINUTES = 5;

    /**
     * Handle DLQ message with persistence and recovery logic
     *
     * @param record Kafka consumer record from DLQ topic
     * @param exception Exception that caused the message to be sent to DLQ
     */
    @Transactional
    public void handle(ConsumerRecord<String, String> record, Exception exception) {
        String correlationId = extractCorrelationId(record);

        log.warn("Processing DLQ message: topic={}, partition={}, offset={}, correlationId={}",
                record.topic(), record.partition(), record.offset(), correlationId);

        try {
            // 1. Persist message to database
            DlqMessage dlqMessage = persistDlqMessage(record, exception, correlationId);

            // 2. Attempt recovery if eligible
            if (dlqMessage.isEligibleForRetry()) {
                attemptRecovery(dlqMessage, record);
            }

            // 3. Alert if max retries exceeded
            if (dlqMessage.getStatus() == DlqMessage.DlqStatus.FAILED && !dlqMessage.getAlerted()) {
                alertOperationsTeam(dlqMessage);
            }

        } catch (Exception e) {
            log.error("Critical: Failed to handle DLQ message. Message may be lost! " +
                     "topic={}, partition={}, offset={}, correlationId={}",
                     record.topic(), record.partition(), record.offset(), correlationId, e);

            // Try to send critical alert even if persistence failed
            try {
                notificationService.sendCriticalDlqAlert(
                    record.topic(),
                    correlationId,
                    "Failed to persist DLQ message: " + e.getMessage()
                );
            } catch (Exception alertException) {
                log.error("Failed to send critical DLQ alert", alertException);
            }
        }
    }

    /**
     * Persist DLQ message to database
     */
    private DlqMessage persistDlqMessage(ConsumerRecord<String, String> record,
                                        Exception exception,
                                        String correlationId) {
        DlqMessage dlqMessage = DlqMessage.builder()
            .originalTopic(getOriginalTopic())
            .dlqTopic(getDlqTopic())
            .partitionNumber(record.partition())
            .offsetNumber(record.offset())
            .messageKey(record.key())
            .messageValue(record.value())
            .headers(extractHeaders(record))
            .failureReason(exception != null ? exception.getMessage() : "Unknown error")
            .stackTrace(exception != null ? getStackTrace(exception) : null)
            .exceptionClass(exception != null ? exception.getClass().getName() : null)
            .correlationId(correlationId)
            .severity(determineSeverity(exception))
            .maxRetryAttempts(MAX_RETRY_ATTEMPTS)
            .build();

        dlqMessage = dlqMessageRepository.save(dlqMessage);

        log.info("DLQ message persisted: id={}, correlationId={}, status={}",
                dlqMessage.getId(), correlationId, dlqMessage.getStatus());

        return dlqMessage;
    }

    /**
     * Attempt to recover the message by reprocessing
     */
    private void attemptRecovery(DlqMessage dlqMessage, ConsumerRecord<String, String> record) {
        dlqMessage.incrementRetryCount();
        dlqMessageRepository.save(dlqMessage);

        log.info("Attempting recovery: id={}, retryCount={}/{}, correlationId={}",
                dlqMessage.getId(), dlqMessage.getRetryCount(),
                dlqMessage.getMaxRetryAttempts(), dlqMessage.getCorrelationId());

        try {
            // Call subclass-specific recovery logic
            boolean recovered = recoverMessage(dlqMessage, record);

            if (recovered) {
                dlqMessage.markAsRecovered("Successfully reprocessed after retry #" +
                                          dlqMessage.getRetryCount());
                dlqMessageRepository.save(dlqMessage);

                log.info("DLQ message recovered: id={}, correlationId={}",
                        dlqMessage.getId(), dlqMessage.getCorrelationId());
            } else {
                handleRecoveryFailure(dlqMessage);
            }

        } catch (Exception e) {
            log.error("Recovery failed: id={}, retryCount={}, correlationId={}",
                     dlqMessage.getId(), dlqMessage.getRetryCount(),
                     dlqMessage.getCorrelationId(), e);

            handleRecoveryFailure(dlqMessage);
        }
    }

    /**
     * Handle recovery failure
     */
    private void handleRecoveryFailure(DlqMessage dlqMessage) {
        if (dlqMessage.getRetryCount() >= dlqMessage.getMaxRetryAttempts()) {
            dlqMessage.markAsFailed("Max retry attempts (" +
                                   dlqMessage.getMaxRetryAttempts() + ") exceeded");
            dlqMessageRepository.save(dlqMessage);

            log.error("DLQ message marked as FAILED after max retries: id={}, correlationId={}",
                     dlqMessage.getId(), dlqMessage.getCorrelationId());
        } else {
            dlqMessage.setStatus(DlqMessage.DlqStatus.PENDING_REVIEW);
            dlqMessageRepository.save(dlqMessage);

            log.warn("DLQ message will be retried later: id={}, retryCount={}/{}, correlationId={}",
                    dlqMessage.getId(), dlqMessage.getRetryCount(),
                    dlqMessage.getMaxRetryAttempts(), dlqMessage.getCorrelationId());
        }
    }

    /**
     * Alert operations team about failed message
     */
    private void alertOperationsTeam(DlqMessage dlqMessage) {
        try {
            notificationService.sendDlqAlert(
                dlqMessage.getOriginalTopic(),
                dlqMessage.getCorrelationId(),
                dlqMessage.getFailureReason(),
                dlqMessage.getRetryCount(),
                dlqMessage.getSeverity().toString()
            );

            dlqMessage.setAlerted(true);
            dlqMessageRepository.save(dlqMessage);

            log.info("Operations team alerted about DLQ failure: id={}, correlationId={}",
                    dlqMessage.getId(), dlqMessage.getCorrelationId());

        } catch (Exception e) {
            log.error("Failed to alert operations team: id={}, correlationId={}",
                     dlqMessage.getId(), dlqMessage.getCorrelationId(), e);
        }
    }

    /**
     * Extract correlation ID from Kafka headers or generate new one
     */
    private String extractCorrelationId(ConsumerRecord<String, String> record) {
        Header correlationHeader = record.headers().lastHeader("correlation-id");
        if (correlationHeader != null) {
            return new String(correlationHeader.value(), StandardCharsets.UTF_8);
        }

        // Generate new correlation ID if not present
        String correlationId = UUID.randomUUID().toString();
        log.debug("Generated new correlation ID: {}", correlationId);
        return correlationId;
    }

    /**
     * Extract all headers as JSON string
     */
    private String extractHeaders(ConsumerRecord<String, String> record) {
        try {
            Map<String, String> headerMap = new HashMap<>();
            record.headers().forEach(header ->
                headerMap.put(header.key(),
                            new String(header.value(), StandardCharsets.UTF_8))
            );
            return objectMapper.writeValueAsString(headerMap);
        } catch (Exception e) {
            log.warn("Failed to serialize headers", e);
            return "{}";
        }
    }

    /**
     * Get stack trace as string
     */
    private String getStackTrace(Exception exception) {
        StringWriter sw = new StringWriter();
        exception.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Determine severity based on exception type
     */
    private DlqMessage.Severity determineSeverity(Exception exception) {
        if (exception == null) {
            return DlqMessage.Severity.MEDIUM;
        }

        String className = exception.getClass().getSimpleName();

        // Critical: Data corruption, security issues
        if (className.contains("Security") || className.contains("Fraud") ||
            className.contains("Corruption")) {
            return DlqMessage.Severity.CRITICAL;
        }

        // High: Business logic failures, validation errors
        if (className.contains("Business") || className.contains("Validation") ||
            className.contains("Constraint")) {
            return DlqMessage.Severity.HIGH;
        }

        // Low: Transient errors, network issues
        if (className.contains("Timeout") || className.contains("Connection") ||
            className.contains("Unavailable")) {
            return DlqMessage.Severity.LOW;
        }

        // Default: Medium
        return DlqMessage.Severity.MEDIUM;
    }

    /**
     * Subclass-specific recovery logic
     *
     * @param dlqMessage The persisted DLQ message
     * @param record The original Kafka record
     * @return true if message was successfully recovered, false otherwise
     */
    protected abstract boolean recoverMessage(DlqMessage dlqMessage,
                                             ConsumerRecord<String, String> record);

    /**
     * Get the original topic name
     */
    protected abstract String getOriginalTopic();

    /**
     * Get the DLQ topic name
     */
    protected abstract String getDlqTopic();
}

package com.waqiti.risk.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DLQ Recovery Service
 *
 * Centralized service for handling DLQ message recovery including:
 * - Logging and monitoring
 * - Retry scheduling
 * - Alert generation
 * - Manual review persistence
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DlqRecoveryService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    private static final String DLQ_MONITORING_TOPIC = "dlq-monitoring";
    private static final String CRITICAL_ALERTS_TOPIC = "critical-alerts";
    private static final String RETRY_TOPIC_SUFFIX = ".retry";

    /**
     * Record DLQ failure for monitoring
     */
    public void recordFailure(Object record, String topic, Exception exception) {
        try {
            Map<String, Object> failureRecord = new HashMap<>();
            failureRecord.put("topic", topic);
            failureRecord.put("timestamp", LocalDateTime.now());
            failureRecord.put("errorMessage", exception.getMessage());
            failureRecord.put("errorClass", exception.getClass().getName());
            failureRecord.put("record", record);

            // Publish to monitoring topic
            kafkaTemplate.send(DLQ_MONITORING_TOPIC, failureRecord);

            // Record metric
            meterRegistry.counter("dlq.failures",
                "topic", topic,
                "error_type", exception.getClass().getSimpleName()
            ).increment();

            log.warn("DLQ failure recorded: topic={}, error={}", topic, exception.getMessage());

        } catch (Exception e) {
            log.error("Failed to record DLQ failure", e);
        }
    }

    /**
     * Send critical alert
     */
    public void sendCriticalAlert(String alertType, String topic, String message) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("alertType", alertType);
            alert.put("topic", topic);
            alert.put("message", message);
            alert.put("timestamp", LocalDateTime.now());
            alert.put("severity", "CRITICAL");

            kafkaTemplate.send(CRITICAL_ALERTS_TOPIC, alert);

            log.error("CRITICAL ALERT: type={}, topic={}, message={}", alertType, topic, message);

        } catch (Exception e) {
            log.error("Failed to send critical alert", e);
        }
    }

    /**
     * Schedule retry for recoverable failure
     */
    public void scheduleRetry(Object record, String originalTopic, long backoffMs) {
        try {
            String retryTopic = originalTopic + RETRY_TOPIC_SUFFIX;

            Message<Object> message = MessageBuilder
                .withPayload(record)
                .setHeader(KafkaHeaders.TOPIC, retryTopic)
                .setHeader("X-Retry-Count", 1)
                .setHeader("X-Original-Topic", originalTopic)
                .setHeader("X-Retry-Scheduled-At", System.currentTimeMillis())
                .setHeader("X-Backoff-Ms", backoffMs)
                .build();

            kafkaTemplate.send(message);

            meterRegistry.counter("dlq.retries.scheduled",
                "topic", originalTopic
            ).increment();

            log.info("Retry scheduled: topic={}, backoff={}ms", originalTopic, backoffMs);

        } catch (Exception e) {
            log.error("Failed to schedule retry", e);
        }
    }

    /**
     * Persist message for manual review
     */
    public void persistForManualReview(Object record, String topic, Exception exception) {
        try {
            Map<String, Object> reviewRecord = new HashMap<>();
            reviewRecord.put("topic", topic);
            reviewRecord.put("record", record);
            reviewRecord.put("exception", exception.getMessage());
            reviewRecord.put("stackTrace", getStackTrace(exception));
            reviewRecord.put("timestamp", LocalDateTime.now());
            reviewRecord.put("status", "PENDING_REVIEW");

            // In production, this would persist to a database
            // For now, we'll send to a manual review topic
            kafkaTemplate.send("manual-review-queue", reviewRecord);

            meterRegistry.counter("dlq.manual_review",
                "topic", topic
            ).increment();

            log.warn("Message persisted for manual review: topic={}", topic);

        } catch (Exception e) {
            log.error("Failed to persist message for manual review", e);
        }
    }

    /**
     * Check if error is recoverable
     */
    public boolean isRecoverable(Exception exception) {
        // Transient errors that might succeed on retry
        String errorMessage = exception.getMessage();
        if (errorMessage == null) {
            return false;
        }

        return errorMessage.contains("timeout") ||
               errorMessage.contains("connection") ||
               errorMessage.contains("unavailable") ||
               errorMessage.contains("temporarily") ||
               exception instanceof org.springframework.dao.TransientDataAccessException ||
               exception instanceof java.net.SocketTimeoutException;
    }

    /**
     * Determine if message/event is critical
     */
    public boolean isCritical(Object record, String topic) {
        // Critical topics that require immediate attention
        return topic.contains("high-risk") ||
               topic.contains("fraud") ||
               topic.contains("critical") ||
               topic.contains("alert");
    }

    /**
     * Calculate exponential backoff
     */
    public long calculateBackoff(int retryCount) {
        // Exponential backoff: 2^retryCount * 1000ms (max 5 minutes)
        long backoff = (long) Math.pow(2, retryCount) * 1000L;
        return Math.min(backoff, 300000L); // Max 5 minutes
    }

    /**
     * Get simplified stack trace
     */
    private String getStackTrace(Exception exception) {
        StringBuilder sb = new StringBuilder();
        sb.append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");

        StackTraceElement[] trace = exception.getStackTrace();
        int limit = Math.min(5, trace.length);
        for (int i = 0; i < limit; i++) {
            sb.append("\tat ").append(trace[i]).append("\n");
        }

        if (trace.length > 5) {
            sb.append("\t... ").append(trace.length - 5).append(" more");
        }

        return sb.toString();
    }
}

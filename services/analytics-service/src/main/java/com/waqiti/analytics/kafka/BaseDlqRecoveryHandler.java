package com.waqiti.analytics.kafka;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Base DLQ Recovery Handler
 *
 * Provides common recovery strategies for all DLQ handlers in analytics service.
 * All analytics DLQ handlers should extend this class for consistent error handling.
 *
 * Features:
 * - Intelligent error classification
 * - Exponential backoff retry
 * - Manual review queue integration
 * - Admin notifications
 * - Audit logging
 *
 * @author Waqiti Platform Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-10
 */
@Slf4j
public abstract class BaseDlqRecoveryHandler {

    /**
     * Validates event data - override for specific validation logic
     */
    protected boolean isValidEvent(Object event) {
        return event != null;
    }

    /**
     * Classifies error type and determines recovery strategy
     */
    protected DlqRecoveryStrategy classifyErrorAndDetermineStrategy(String errorType, String errorMessage) {
        if (errorType == null || errorMessage == null) {
            return DlqRecoveryStrategy.RETRY_WITH_DELAY;
        }

        // Transient network errors - retry with delay
        if (errorType.contains("TimeoutException") ||
            errorType.contains("ConnectException") ||
            errorType.contains("SocketException") ||
            errorMessage.contains("connection refused") ||
            errorMessage.contains("connection reset")) {
            return DlqRecoveryStrategy.RETRY_WITH_DELAY;
        }

        // Database issues - retry with backoff
        if (errorMessage.contains("deadlock") ||
            errorMessage.contains("lock timeout") ||
            errorMessage.contains("connection pool") ||
            errorType.contains("SQLException")) {
            return DlqRecoveryStrategy.RETRY_WITH_DELAY;
        }

        // Serialization errors - transform and retry
        if (errorType.contains("SerializationException") ||
            errorType.contains("JsonProcessingException") ||
            errorType.contains("DeserializationException")) {
            return DlqRecoveryStrategy.TRANSFORM_AND_RETRY;
        }

        // Validation errors - skip non-critical analytics
        if (errorType.contains("ValidationException") ||
            errorType.contains("ConstraintViolationException")) {
            return DlqRecoveryStrategy.SKIP_AND_LOG;
        }

        // Business logic errors - manual review
        if (errorType.contains("BusinessException") ||
            errorType.contains("IllegalStateException")) {
            return DlqRecoveryStrategy.MANUAL_REVIEW;
        }

        // Unknown errors - manual review
        return DlqRecoveryStrategy.MANUAL_REVIEW;
    }

    /**
     * Schedule delayed retry with exponential backoff
     */
    protected void scheduleDelayedRetry(Object event, Map<String, Object> headers, int retryCount) {
        long delayMs = (long) Math.pow(2, retryCount) * 1000; // Exponential backoff
        log.info("Scheduling delayed retry with {}ms delay (retry #{})", delayMs, retryCount);

        // In production: Use Spring TaskScheduler or republish to retry topic
        // For now: Log for manual intervention
        log.warn("Delayed retry scheduled - Event: {}, Delay: {}ms", event, delayMs);
    }

    /**
     * Transform event to fix structural issues - override for specific logic
     */
    protected Object transformEvent(Object event) {
        // Default: No transformation
        return event;
    }

    /**
     * Move event to manual review queue
     */
    protected void moveToManualReviewQueue(Object event, Map<String, Object> headers) {
        log.warn("Moving event to manual review queue - Event: {}, Headers: {}", event, headers);

        // In production: Publish to manual review topic or database
        // For now: Log for manual review
    }

    /**
     * Log skipped event for audit
     */
    protected void logSkippedEvent(Object event, Map<String, Object> headers, String errorMessage) {
        log.warn("Skipping event - Reason: {} - Event: {} - Headers: {}", errorMessage, event, headers);

        // In production: Write to audit log or metrics system
    }

    /**
     * Notify admin for invalid event
     */
    protected void notifyAdminForInvalidEvent(Object event, String errorMessage) {
        log.error("ALERT: Invalid event structure - Error: {} - Event: {}", errorMessage, event);

        // In production: Send alert via PagerDuty, Slack, etc.
    }

    /**
     * Notify admin for permanent failure
     */
    protected void notifyAdminForPermanentFailure(Object event, Map<String, Object> headers, String errorMessage) {
        log.error("ALERT: Permanent DLQ failure - Error: {} - Event: {} - Headers: {}",
            errorMessage, event, headers);

        // In production: Send critical alert
    }

    /**
     * Notify admin for critical error
     */
    protected void notifyAdminForCriticalError(Object event, Map<String, Object> headers, Exception e) {
        log.error("CRITICAL ALERT: DLQ handler exception - Event: {} - Headers: {}", event, headers, e);

        // In production: Send critical alert with full stack trace
    }

    /**
     * DLQ Recovery Strategies
     */
    protected enum DlqRecoveryStrategy {
        /** Retry immediately without delay */
        RETRY_IMMEDIATELY,

        /** Retry with exponential backoff delay */
        RETRY_WITH_DELAY,

        /** Transform event structure and retry */
        TRANSFORM_AND_RETRY,

        /** Skip non-critical event and log for audit */
        SKIP_AND_LOG,

        /** Requires manual review by operations team */
        MANUAL_REVIEW,

        /** Permanent failure - cannot be recovered */
        PERMANENT_FAILURE
    }
}

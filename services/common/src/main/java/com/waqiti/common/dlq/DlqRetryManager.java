package com.waqiti.common.dlq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages retry logic for DLQ messages with exponential backoff and intelligent retry decisions.
 * Provides automated retry scheduling and manual retry capabilities.
 */
@Component
@Slf4j
public class DlqRetryManager {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService retryScheduler;

    // Retry tracking
    private final ConcurrentHashMap<String, RetryMetadata> retryTracking = new ConcurrentHashMap<>();

    // Configuration
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofMinutes(5);
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(4);

    // Metrics
    private Counter retryScheduledCounter;
    private Counter retryExecutedCounter;
    private Counter retrySuccessCounter;
    private Counter retryFailedCounter;

    public DlqRetryManager(KafkaTemplate<String, Object> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.retryScheduler = Executors.newScheduledThreadPool(10);
    }

    @PostConstruct
    public void initMetrics() {
        retryScheduledCounter = Counter.builder("dlq_retries_scheduled_total")
            .description("Total number of DLQ retries scheduled")
            .register(meterRegistry);
        retryExecutedCounter = Counter.builder("dlq_retries_executed_total")
            .description("Total number of DLQ retries executed")
            .register(meterRegistry);
        retrySuccessCounter = Counter.builder("dlq_retries_success_total")
            .description("Total number of successful DLQ retries")
            .register(meterRegistry);
        retryFailedCounter = Counter.builder("dlq_retries_failed_total")
            .description("Total number of failed DLQ retries")
            .register(meterRegistry);
    }

    /**
     * Determines if a DLQ message should be retried based on error category and history.
     */
    public boolean shouldRetry(DlqMessage dlqMessage) {
        String messageId = dlqMessage.getMessageId();
        RetryMetadata metadata = retryTracking.get(messageId);

        // Check if we've exceeded max retry attempts
        int currentRetryCount = metadata != null ? metadata.getRetryCount() : 0;
        if (currentRetryCount >= MAX_RETRY_ATTEMPTS) {
            log.info("Max retry attempts reached for message: {}", messageId);
            return false;
        }

        // Check error category eligibility for retry
        if (!isRetryEligible(dlqMessage.getErrorCategory())) {
            log.info("Error category not eligible for retry: category={}, messageId={}",
                dlqMessage.getErrorCategory(), messageId);
            return false;
        }

        // Check if enough time has passed since last retry
        if (metadata != null && !hasEnoughTimePassed(metadata)) {
            log.info("Not enough time passed since last retry: messageId={}", messageId);
            return false;
        }

        return true;
    }

    /**
     * Schedules a retry for a DLQ message with exponential backoff.
     */
    public void scheduleRetry(DlqMessage dlqMessage) {
        String messageId = dlqMessage.getMessageId();
        RetryMetadata metadata = retryTracking.computeIfAbsent(messageId, k -> new RetryMetadata());

        int retryCount = metadata.incrementRetryCount();
        Duration delay = calculateRetryDelay(retryCount);
        Instant scheduledTime = Instant.now().plus(delay);

        metadata.setLastRetryTime(Instant.now());
        metadata.setNextRetryTime(scheduledTime);

        // Update DLQ message with retry information
        dlqMessage.setRetryCount(retryCount);
        dlqMessage.setMaxRetries(MAX_RETRY_ATTEMPTS);

        log.info("Scheduling retry for DLQ message: messageId={}, retryCount={}, delay={}",
            messageId, retryCount, delay);

        retryScheduler.schedule(
            () -> executeRetry(dlqMessage),
            delay.toMillis(),
            TimeUnit.MILLISECONDS
        );

        retryScheduledCounter.increment();
    }

    /**
     * Executes a retry attempt for a DLQ message.
     */
    @Async
    public CompletableFuture<Void> executeRetry(DlqMessage dlqMessage) {
        return CompletableFuture.runAsync(() -> {
            String messageId = dlqMessage.getMessageId();
            String originalTopic = dlqMessage.getOriginalTopic();

            try {
                log.info("Executing retry for DLQ message: messageId={}, retryCount={}",
                    messageId, dlqMessage.getRetryCount());

                retryExecutedCounter.increment();

                // Add retry metadata to the message
                Map<String, Object> retryHeaders = Map.of(
                    "dlq-retry", true,
                    "dlq-retry-count", dlqMessage.getRetryCount(),
                    "dlq-original-message-id", messageId,
                    "dlq-retry-timestamp", Instant.now().toString()
                );

                // Send the original message back to the original topic for retry
                kafkaTemplate.send(originalTopic, null, dlqMessage.getOriginalMessage())
                    .whenComplete((result, failure) -> {
                        if (failure == null) {
                            log.info("Successfully retried DLQ message: messageId={}", messageId);
                            retrySuccessCounter.increment();
                            updateRetrySuccess(messageId);
                        } else {
                            log.error("Failed to retry DLQ message: messageId={}, error={}",
                                messageId, failure.getMessage());
                            retryFailedCounter.increment();
                            handleRetryFailure(dlqMessage, failure);
                        }
                    });

            } catch (Exception e) {
                log.error("Error executing retry for DLQ message: messageId={}, error={}",
                    messageId, e.getMessage(), e);
                retryFailedCounter.increment();
                handleRetryFailure(dlqMessage, e);
            }
        });
    }

    /**
     * Manually triggers a retry for a specific DLQ message (admin operation).
     */
    public void manualRetry(String messageId, DlqMessage dlqMessage) {
        log.info("Manual retry triggered for DLQ message: messageId={}", messageId);

        // Reset retry count for manual retry
        RetryMetadata metadata = retryTracking.get(messageId);
        if (metadata != null) {
            metadata.setManualRetry(true);
        }

        executeRetry(dlqMessage);
    }

    /**
     * Bulk retry operation for multiple DLQ messages.
     */
    public void bulkRetry(Map<String, DlqMessage> messages) {
        log.info("Executing bulk retry for {} messages", messages.size());

        messages.forEach((messageId, dlqMessage) -> {
            if (shouldRetry(dlqMessage)) {
                scheduleRetry(dlqMessage);
            } else {
                log.warn("Skipping retry for message (not eligible): messageId={}", messageId);
            }
        });
    }

    /**
     * Cleans up old retry metadata to prevent memory leaks.
     */
    public void cleanupOldRetryData() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));
        int removed = 0;

        var iterator = retryTracking.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().getLastRetryTime().isBefore(cutoff)) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log.info("Cleaned up {} old retry metadata entries", removed);
        }
    }

    /**
     * Gets retry statistics for monitoring.
     */
    public Map<String, Object> getRetryStatistics() {
        return Map.of(
            "activeRetries", retryTracking.size(),
            "totalScheduled", retryScheduledCounter.count(),
            "totalExecuted", retryExecutedCounter.count(),
            "totalSuccess", retrySuccessCounter.count(),
            "totalFailed", retryFailedCounter.count()
        );
    }

    private boolean isRetryEligible(DlqMessage.ErrorCategory errorCategory) {
        switch (errorCategory) {
            case NETWORK_ERROR:
            case TIMEOUT_ERROR:
            case EXTERNAL_SERVICE_ERROR:
            case DATABASE_ERROR: // Only for transient database errors
                return true;
            case SERIALIZATION_ERROR:
            case VALIDATION_ERROR:
            case AUTHENTICATION_ERROR:
            case AUTHORIZATION_ERROR:
            case CONFIGURATION_ERROR:
                return false; // These typically require manual intervention
            default:
                return false; // Conservative approach for unknown errors
        }
    }

    private boolean hasEnoughTimePassed(RetryMetadata metadata) {
        if (metadata.getNextRetryTime() == null) {
            return true;
        }
        return Instant.now().isAfter(metadata.getNextRetryTime());
    }

    private Duration calculateRetryDelay(int retryCount) {
        long delayMillis = (long) (INITIAL_RETRY_DELAY.toMillis() * Math.pow(BACKOFF_MULTIPLIER, retryCount - 1));
        Duration calculatedDelay = Duration.ofMillis(delayMillis);

        // Cap at maximum delay
        return calculatedDelay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : calculatedDelay;
    }

    private void updateRetrySuccess(String messageId) {
        RetryMetadata metadata = retryTracking.get(messageId);
        if (metadata != null) {
            metadata.setLastSuccessTime(Instant.now());
            metadata.setRetrySuccessful(true);
        }
    }

    private void handleRetryFailure(DlqMessage dlqMessage, Throwable failure) {
        String messageId = dlqMessage.getMessageId();
        RetryMetadata metadata = retryTracking.get(messageId);

        if (metadata != null && metadata.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
            log.error("Max retry attempts exhausted for message: messageId={}", messageId);

            // Send to permanent failure queue
            kafkaTemplate.send("dlq-permanent-failures", Map.of(
                "messageId", messageId,
                "originalMessage", dlqMessage,
                "finalError", failure.getMessage(),
                "retryCount", metadata.getRetryCount(),
                "timestamp", Instant.now()
            ));

            // Remove from retry tracking
            retryTracking.remove(messageId);
        } else {
            // Schedule next retry if we haven't exceeded max attempts
            if (shouldRetry(dlqMessage)) {
                scheduleRetry(dlqMessage);
            }
        }
    }

    /**
     * Internal class to track retry metadata.
     */
    private static class RetryMetadata {
        private int retryCount = 0;
        private Instant lastRetryTime;
        private Instant nextRetryTime;
        private Instant lastSuccessTime;
        private boolean retrySuccessful = false;
        private boolean manualRetry = false;

        public int incrementRetryCount() {
            return ++retryCount;
        }

        // Getters and setters
        public int getRetryCount() { return retryCount; }
        public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
        public Instant getLastRetryTime() { return lastRetryTime; }
        public void setLastRetryTime(Instant lastRetryTime) { this.lastRetryTime = lastRetryTime; }
        public Instant getNextRetryTime() { return nextRetryTime; }
        public void setNextRetryTime(Instant nextRetryTime) { this.nextRetryTime = nextRetryTime; }
        public Instant getLastSuccessTime() { return lastSuccessTime; }
        public void setLastSuccessTime(Instant lastSuccessTime) { this.lastSuccessTime = lastSuccessTime; }
        public boolean isRetrySuccessful() { return retrySuccessful; }
        public void setRetrySuccessful(boolean retrySuccessful) { this.retrySuccessful = retrySuccessful; }
        public boolean isManualRetry() { return manualRetry; }
        public void setManualRetry(boolean manualRetry) { this.manualRetry = manualRetry; }
    }
}
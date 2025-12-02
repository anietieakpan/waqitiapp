package com.waqiti.accounting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.accounting.domain.DlqMessage;
import com.waqiti.accounting.domain.DlqRetryHistory;
import com.waqiti.accounting.domain.DlqStatus;
import com.waqiti.accounting.repository.DlqMessageRepository;
import com.waqiti.accounting.repository.DlqRetryHistoryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DLQ Recovery Service
 * Handles storage, retry, and recovery of failed Kafka messages
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DlqRecoveryService {

    private final DlqMessageRepository dlqMessageRepository;
    private final DlqRetryHistoryRepository dlqRetryHistoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 50;
    private static final int MAX_CONCURRENT_RETRIES = 10;

    /**
     * Store failed message in DLQ for later retry
     */
    @Transactional
    public DlqMessage storeFailedMessage(
            String topic,
            String messageId,
            Map<String, Object> payload,
            Exception exception,
            String consumerGroup,
            Long offset,
            Integer partition) {

        log.warn("Storing failed message in DLQ: topic={}, messageId={}, error={}",
            topic, messageId, exception.getMessage());

        // Check if message already exists (deduplication)
        return dlqMessageRepository.findByMessageId(messageId)
            .map(existing -> {
                log.info("DLQ message already exists, updating: {}", messageId);
                existing.incrementRetry();
                return dlqMessageRepository.save(existing);
            })
            .orElseGet(() -> {
                DlqMessage dlqMessage = DlqMessage.builder()
                    .messageId(messageId)
                    .topic(topic)
                    .messagePayload(payload)
                    .errorMessage(exception.getMessage())
                    .errorStackTrace(getStackTrace(exception))
                    .errorClass(exception.getClass().getName())
                    .retryCount(0)
                    .maxRetryAttempts(5)
                    .status(DlqStatus.PENDING)
                    .consumerGroup(consumerGroup)
                    .originalOffset(offset)
                    .originalPartition(partition)
                    .originalTimestamp(LocalDateTime.now())
                    .firstFailureAt(LocalDateTime.now())
                    .build();

                dlqMessage.calculateNextRetry();
                DlqMessage saved = dlqMessageRepository.save(dlqMessage);

                // Record metrics
                meterRegistry.counter("dlq.messages.stored",
                    "topic", topic,
                    "error_class", exception.getClass().getSimpleName()
                ).increment();

                return saved;
            });
    }

    /**
     * Automated retry processor - runs every minute
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000) // Every 60 seconds
    @Transactional
    public void processAutomatedRetries() {
        try {
            log.debug("Starting automated DLQ retry batch processing");

            List<DlqMessage> messagesToRetry = dlqMessageRepository.findMessagesReadyForRetry(
                LocalDateTime.now(),
                PageRequest.of(0, MAX_CONCURRENT_RETRIES)
            );

            if (messagesToRetry.isEmpty()) {
                log.debug("No DLQ messages ready for retry");
                return;
            }

            log.info("Found {} DLQ messages ready for retry", messagesToRetry.size());

            for (DlqMessage message : messagesToRetry) {
                try {
                    retryMessage(message);
                } catch (OptimisticLockingFailureException e) {
                    log.debug("Message {} already being processed by another instance", message.getMessageId());
                } catch (Exception e) {
                    log.error("Error processing retry for message {}: {}",
                        message.getMessageId(), e.getMessage(), e);
                }
            }

            log.info("Completed automated DLQ retry batch");
        } catch (Exception e) {
            log.error("Error in automated DLQ retry processor: {}", e.getMessage(), e);
        }
    }

    /**
     * Retry a single DLQ message
     */
    @Transactional
    public boolean retryMessage(UUID messageId) {
        DlqMessage message = dlqMessageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("DLQ message not found: " + messageId));

        return retryMessage(message);
    }

    /**
     * Retry a DLQ message
     */
    private boolean retryMessage(DlqMessage message) {
        long startTime = System.currentTimeMillis();

        log.info("Attempting retry {} of {} for message: topic={}, messageId={}",
            message.getRetryCount() + 1,
            message.getMaxRetryAttempts(),
            message.getTopic(),
            message.getMessageId());

        // Update status to RETRYING
        message.setStatus(DlqStatus.RETRYING);
        dlqMessageRepository.save(message);

        DlqRetryHistory.RetryStatus retryStatus;
        String errorMessage = null;

        try {
            // Attempt to republish the message to original topic
            kafkaTemplate.send(message.getTopic(), message.getPartitionKey(), message.getMessagePayload())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to republish message {}: {}", message.getMessageId(), ex.getMessage());
                    } else {
                        log.info("Successfully republished message {} to topic {}",
                            message.getMessageId(), message.getTopic());
                    }
                });

            // Mark as resolved
            message.markResolved("SYSTEM", "Automatically retried and republished");
            retryStatus = DlqRetryHistory.RetryStatus.SUCCESS;

            // Record metrics
            meterRegistry.counter("dlq.retries.success",
                "topic", message.getTopic()
            ).increment();

        } catch (Exception e) {
            log.error("Retry failed for message {}: {}", message.getMessageId(), e.getMessage());

            errorMessage = e.getMessage();
            retryStatus = DlqRetryHistory.RetryStatus.FAILURE;

            // Increment retry count and calculate next retry time
            message.incrementRetry();

            // Record metrics
            meterRegistry.counter("dlq.retries.failed",
                "topic", message.getTopic(),
                "error_class", e.getClass().getSimpleName()
            ).increment();
        }

        // Save updated message
        dlqMessageRepository.save(message);

        // Record retry history
        DlqRetryHistory history = DlqRetryHistory.builder()
            .dlqMessageId(message.getId())
            .retryAttempt(message.getRetryCount())
            .retryTimestamp(LocalDateTime.now())
            .retryStatus(retryStatus)
            .errorMessage(errorMessage)
            .processingDurationMs(System.currentTimeMillis() - startTime)
            .build();

        dlqRetryHistoryRepository.save(history);

        return retryStatus == DlqRetryHistory.RetryStatus.SUCCESS;
    }

    /**
     * Mark message for manual review
     */
    @Transactional
    public void markForManualReview(UUID messageId, String reason) {
        DlqMessage message = dlqMessageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("DLQ message not found: " + messageId));

        message.setStatus(DlqStatus.MANUAL_REVIEW);
        message.setResolutionNotes(reason);
        dlqMessageRepository.save(message);

        log.warn("DLQ message {} marked for manual review: {}", messageId, reason);

        meterRegistry.counter("dlq.manual_review",
            "topic", message.getTopic()
        ).increment();
    }

    /**
     * Manually resolve a DLQ message
     */
    @Transactional
    public void resolveMessage(UUID messageId, String resolvedBy, String notes) {
        DlqMessage message = dlqMessageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("DLQ message not found: " + messageId));

        message.markResolved(resolvedBy, notes);
        dlqMessageRepository.save(message);

        log.info("DLQ message {} manually resolved by {}", messageId, resolvedBy);
    }

    /**
     * Get DLQ statistics
     */
    public Map<String, Object> getDlqStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("pending", dlqMessageRepository.countByStatus(DlqStatus.PENDING));
        stats.put("retrying", dlqMessageRepository.countByStatus(DlqStatus.RETRYING));
        stats.put("resolved", dlqMessageRepository.countByStatus(DlqStatus.RESOLVED));
        stats.put("failed", dlqMessageRepository.countByStatus(DlqStatus.FAILED));
        stats.put("manual_review", dlqMessageRepository.countByStatus(DlqStatus.MANUAL_REVIEW));

        // Get per-topic statistics from last 24 hours
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<DlqMessageRepository.DlqStatistic> topicStats =
            dlqMessageRepository.getDlqStatistics(since);

        stats.put("topic_statistics", topicStats);

        return stats;
    }

    /**
     * Check alert thresholds and send alerts if breached
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000) // Every 5 minutes
    public void checkAlertThresholds() {
        // This would integrate with actual alerting service (Slack, PagerDuty, etc.)
        // For now, just log warnings

        long manualReviewCount = dlqMessageRepository.countByStatus(DlqStatus.MANUAL_REVIEW);
        if (manualReviewCount > 0) {
            log.warn("DLQ ALERT: {} messages requiring manual review", manualReviewCount);
        }

        long failedCount = dlqMessageRepository.countByStatus(DlqStatus.FAILED);
        if (failedCount > 10) {
            log.warn("DLQ ALERT: {} messages have exhausted all retries", failedCount);
        }

        long pendingCount = dlqMessageRepository.countByStatus(DlqStatus.PENDING);
        if (pendingCount > 100) {
            log.warn("DLQ ALERT: {} messages pending retry (queue backlog)", pendingCount);
        }
    }

    /**
     * Cleanup old resolved messages (run daily)
     */
    @Scheduled(cron = "0 0 3 * * ?") // 3 AM daily
    @Transactional
    public void cleanupOldResolvedMessages() {
        log.info("Starting cleanup of old DLQ resolved messages");

        // Delete messages resolved more than 30 days ago
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        long deleted = dlqMessageRepository.deleteByStatusAndResolvedAtBefore(
            DlqStatus.RESOLVED, cutoff);

        log.info("Deleted {} old resolved DLQ messages", deleted);

        // Also cleanup old retry history (older than 90 days)
        LocalDateTime historyyCutoff = LocalDateTime.now().minusDays(90);
        long historyDeleted = dlqRetryHistoryRepository.deleteByRetryTimestampBefore(historyCutoff);

        log.info("Deleted {} old DLQ retry history records", historyDeleted);
    }

    /**
     * Get stack trace as string
     */
    private String getStackTrace(Exception e) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return sw.toString();
        } catch (Exception ex) {
            return "Unable to capture stack trace";
        }
    }
}

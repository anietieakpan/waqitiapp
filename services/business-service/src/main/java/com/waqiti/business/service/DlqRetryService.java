package com.waqiti.business.service;

import com.waqiti.business.domain.DlqMessage;
import com.waqiti.business.repository.DlqMessageRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DLQ Retry Service with Exponential Backoff
 *
 * Automatically retries failed Kafka messages from the DLQ with exponential backoff.
 * Ensures financial data is never lost due to transient failures.
 *
 * Retry Schedule (exponential backoff):
 * - Attempt 1: Immediate (0 minutes)
 * - Attempt 2: 2 minutes
 * - Attempt 3: 4 minutes
 * - Attempt 4: 8 minutes
 * - Attempt 5: 16 minutes
 * - Max retries exceeded: Manual intervention required
 *
 * CRITICAL: Financial service - comprehensive error recovery
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-01-16
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DlqRetryService {

    private final DlqMessageRepository dlqMessageRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * Persist a failed message to the DLQ for retry
     *
     * @param consumerName Name of the consumer that failed
     * @param originalTopic Original Kafka topic
     * @param messageKey Message key for deduplication
     * @param messagePayload The actual message payload
     * @param headers Kafka headers
     * @param error The exception that caused the failure
     * @return The persisted DLQ message
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DlqMessage persistFailedMessage(
            String consumerName,
            String originalTopic,
            Integer partition,
            Long offset,
            String messageKey,
            Map<String, Object> messagePayload,
            Map<String, Object> headers,
            Exception error) {

        log.warn("Persisting failed message to DLQ: consumer={}, topic={}, key={}",
                consumerName, originalTopic, messageKey);

        // Check for duplicate (idempotency)
        if (messageKey != null) {
            var existing = dlqMessageRepository.findFirstByMessageKeyOrderByCreatedAtDesc(messageKey);
            if (existing.isPresent()) {
                var existingMsg = existing.get();
                if (existingMsg.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(5))) {
                    log.info("Duplicate DLQ message detected within 5 minutes, updating existing: id={}",
                            existingMsg.getId());
                    existingMsg.setRetryCount(existingMsg.getRetryCount() + 1);
                    existingMsg.setErrorMessage(error.getMessage());
                    existingMsg.setErrorStackTrace(getStackTrace(error));
                    return dlqMessageRepository.save(existingMsg);
                }
            }
        }

        DlqMessage dlqMessage = DlqMessage.builder()
                .consumerName(consumerName)
                .originalTopic(originalTopic)
                .originalPartition(partition)
                .originalOffset(offset)
                .messageKey(messageKey)
                .messagePayload(messagePayload)
                .headers(headers)
                .errorMessage(error.getMessage())
                .errorStackTrace(getStackTrace(error))
                .status(DlqMessage.DlqStatus.PENDING)
                .retryCount(0)
                .maxRetries(5)
                .retryAfter(LocalDateTime.now().plusMinutes(2)) // First retry after 2 minutes
                .build();

        DlqMessage saved = dlqMessageRepository.save(dlqMessage);

        // Record metrics
        meterRegistry.counter("dlq.message.persisted",
                "consumer", consumerName,
                "topic", originalTopic).increment();

        log.info("DLQ message persisted: id={}, consumer={}, will retry at {}",
                saved.getId(), consumerName, saved.getRetryAfter());

        return saved;
    }

    /**
     * Scheduled job to process eligible DLQ messages (runs every minute)
     */
    @Scheduled(fixedRate = 60000) // Every 60 seconds
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void processRetryQueue() {
        try {
            List<DlqMessage> eligibleMessages = dlqMessageRepository
                    .findMessagesEligibleForRetry(LocalDateTime.now());

            if (eligibleMessages.isEmpty()) {
                return;
            }

            log.info("Processing {} eligible DLQ messages for retry", eligibleMessages.size());

            for (DlqMessage message : eligibleMessages) {
                processRetry(message);
            }

        } catch (Exception e) {
            log.error("Error processing DLQ retry queue", e);
            meterRegistry.counter("dlq.retry.queue.error").increment();
        }
    }

    /**
     * Process a single retry attempt
     */
    private void processRetry(DlqMessage message) {
        try {
            log.info("Retrying DLQ message: id={}, attempt={}/{}",
                    message.getId(), message.getRetryCount() + 1, message.getMaxRetries());

            message.setStatus(DlqMessage.DlqStatus.RETRYING);
            dlqMessageRepository.save(message);

            // Republish to original topic
            kafkaTemplate.send(
                    message.getOriginalTopic(),
                    message.getMessageKey(),
                    message.getMessagePayload()
            ).whenComplete((result, ex) -> {
                if (ex != null) {
                    handleRetryFailure(message, ex);
                } else {
                    handleRetrySuccess(message);
                }
            });

        } catch (Exception e) {
            handleRetryFailure(message, e);
        }
    }

    /**
     * Handle successful retry
     */
    @Transactional
    protected void handleRetrySuccess(DlqMessage message) {
        log.info("DLQ message successfully recovered: id={}, attempts={}",
                message.getId(), message.getRetryCount() + 1);

        message.markRecovered("AUTO_RETRY");
        dlqMessageRepository.save(message);

        meterRegistry.counter("dlq.message.recovered",
                "consumer", message.getConsumerName(),
                "attempts", String.valueOf(message.getRetryCount() + 1)).increment();
    }

    /**
     * Handle failed retry
     */
    @Transactional
    protected void handleRetryFailure(DlqMessage message, Throwable error) {
        log.warn("DLQ message retry failed: id={}, attempt={}, error={}",
                message.getId(), message.getRetryCount() + 1, error.getMessage());

        boolean hasMoreRetries = message.incrementRetryCount();

        if (!hasMoreRetries) {
            log.error("DLQ message exceeded max retries, requiring manual intervention: id={}",
                    message.getId());

            message.markManualIntervention(
                    String.format("Max retries (%d) exceeded. Last error: %s",
                            message.getMaxRetries(), error.getMessage())
            );

            meterRegistry.counter("dlq.message.max_retries_exceeded",
                    "consumer", message.getConsumerName()).increment();

            // TODO: Send alert to operations team
            sendManualInterventionAlert(message);
        } else {
            log.info("DLQ message scheduled for retry: id={}, nextRetry={}, attempt={}/{}",
                    message.getId(), message.getRetryAfter(),
                    message.getRetryCount(), message.getMaxRetries());
        }

        dlqMessageRepository.save(message);

        meterRegistry.counter("dlq.retry.failed",
                "consumer", message.getConsumerName(),
                "attempt", String.valueOf(message.getRetryCount())).increment();
    }

    /**
     * Manually retry a specific message
     */
    @Transactional
    public void manualRetry(UUID messageId, String triggeredBy) {
        DlqMessage message = dlqMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("DLQ message not found: " + messageId));

        log.info("Manual retry triggered: id={}, by={}", messageId, triggeredBy);

        message.scheduleImmediateRetry();
        message.setProcessingNotes("Manual retry triggered by: " + triggeredBy);
        dlqMessageRepository.save(message);

        // Process immediately
        processRetry(message);

        meterRegistry.counter("dlq.manual.retry",
                "consumer", message.getConsumerName(),
                "triggered_by", triggeredBy).increment();
    }

    /**
     * Mark a message as permanently failed
     */
    @Transactional
    public void markPermanentFailure(UUID messageId, String reason, String markedBy) {
        DlqMessage message = dlqMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("DLQ message not found: " + messageId));

        log.warn("Marking DLQ message as permanent failure: id={}, reason={}, by={}",
                messageId, reason, markedBy);

        message.setStatus(DlqMessage.DlqStatus.PERMANENT_FAILURE);
        message.setProcessingNotes(reason);
        message.setResolvedBy(markedBy);
        message.setResolvedAt(LocalDateTime.now());

        dlqMessageRepository.save(message);

        meterRegistry.counter("dlq.permanent.failure",
                "consumer", message.getConsumerName()).increment();
    }

    /**
     * Get DLQ statistics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics() {
        LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);

        return Map.of(
                "pending", dlqMessageRepository.countByStatus(DlqMessage.DlqStatus.PENDING),
                "retryScheduled", dlqMessageRepository.countByStatus(DlqMessage.DlqStatus.RETRY_SCHEDULED),
                "recovered", dlqMessageRepository.countByStatus(DlqMessage.DlqStatus.RECOVERED),
                "manualInterventionRequired", dlqMessageRepository.countByStatus(DlqMessage.DlqStatus.MANUAL_INTERVENTION_REQUIRED),
                "maxRetriesExceeded", dlqMessageRepository.countByStatus(DlqMessage.DlqStatus.MAX_RETRIES_EXCEEDED),
                "permanentFailures", dlqMessageRepository.countByStatus(DlqMessage.DlqStatus.PERMANENT_FAILURE),
                "maxRetriesExceededLast24h", dlqMessageRepository.countMaxRetriesExceededSince(last24Hours)
        );
    }

    /**
     * Send alert for manual intervention required
     */
    private void sendManualInterventionAlert(DlqMessage message) {
        // TODO: Integrate with notification service
        log.error("ALERT: DLQ message requires manual intervention: " +
                        "id={}, consumer={}, topic={}, error={}",
                message.getId(), message.getConsumerName(),
                message.getOriginalTopic(), message.getErrorMessage());
    }

    /**
     * Extract stack trace from exception
     */
    private String getStackTrace(Throwable throwable) {
        if (throwable == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getName()).append(": ").append(throwable.getMessage()).append("\n");

        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
            if (sb.length() > 5000) break; // Limit stack trace size
        }

        if (throwable.getCause() != null) {
            sb.append("\nCaused by: ");
            sb.append(getStackTrace(throwable.getCause()));
        }

        return sb.toString();
    }
}

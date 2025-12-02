package com.waqiti.common.kafka.dlq;

import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import com.waqiti.common.kafka.dlq.repository.DlqRecordRepository;
import com.waqiti.common.kafka.dlq.strategy.RecoveryStrategyFactory;
import com.waqiti.common.kafka.dlq.strategy.RecoveryStrategyHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * DLQ Processor Service - Main orchestrator for DLQ message recovery.
 *
 * This service runs scheduled jobs to process messages in the dead letter queue:
 * 1. Fetch messages ready for retry
 * 2. Select appropriate recovery strategy via factory
 * 3. Execute recovery
 * 4. Update message status
 * 5. Record comprehensive metrics
 *
 * PROCESSING SCHEDULE:
 * - Every 2 minutes: Process messages ready for retry
 * - Every 15 minutes: Check for stuck messages
 * - Every hour: Clean up old successfully processed messages
 *
 * RECOVERY STRATEGIES (via Factory):
 * - SkipStrategy: Invalid/obsolete messages
 * - AutomaticRetryStrategy: Transient errors with exponential backoff
 * - CompensatingTransactionStrategy: Financial transaction reversals
 * - ManualInterventionStrategy: Complex issues requiring human review
 *
 * PRODUCTION CONFIGURATION:
 * - Enable: dlq.processor.enabled=true (default: true)
 * - Batch size: dlq.processor.batch-size (default: 100)
 * - Retention: dlq.processor.retention-days (default: 30)
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-11-19
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dlq.processor.enabled", havingValue = "true", matchIfMissing = true)
public class DLQProcessorService {

    private final DlqRecordRepository dlqRecordRepository;
    private final RecoveryStrategyFactory strategyFactory;
    private final MeterRegistry meterRegistry;

    private static final int MAX_MESSAGES_PER_BATCH = 100;
    private static final int RETENTION_DAYS = 30;

    /**
     * Main processing loop - runs every 2 minutes.
     * Processes all messages ready for retry regardless of event type.
     */
    @Scheduled(cron = "0 */2 * * * *") // Every 2 minutes
    @Transactional
    public void processReadyMessages() {
        log.debug("🔄 Starting DLQ message processing");
        Timer.Sample timer = Timer.start(meterRegistry);

        try {
            // Find messages ready for retry
            Instant now = Instant.now();
            List<DlqRecordEntity> messages = dlqRecordRepository
                    .findByNextRetryTimeBeforeAndStatus(now, DlqStatus.PENDING)
                    .stream()
                    .limit(MAX_MESSAGES_PER_BATCH)
                    .toList();

            if (messages.isEmpty()) {
                log.debug("No DLQ messages ready for processing");
                timer.stop(meterRegistry.timer("dlq.processor.batch.time", "result", "empty"));
                return;
            }

            log.info("📊 Processing {} DLQ messages", messages.size());

            // Process each message
            ProcessingStats stats = new ProcessingStats();
            for (DlqRecordEntity message : messages) {
                RecoveryOutcome outcome = processMessage(message);
                stats.record(outcome);
            }

            log.info("✅ DLQ batch completed: success={}, retryScheduled={}, skipped={}, manualReview={}",
                    stats.successCount, stats.retryScheduledCount, stats.skippedCount, stats.manualReviewCount);

            // Record metrics
            recordBatchMetrics(stats);
            timer.stop(meterRegistry.timer("dlq.processor.batch.time", "result", "success"));

        } catch (Exception e) {
            log.error("❌ DLQ processing batch failed: {}", e.getMessage(), e);
            timer.stop(meterRegistry.timer("dlq.processor.batch.time", "result", "error"));
        }
    }

    /**
     * Checks for stuck messages every 15 minutes.
     * Stuck messages are those that haven't been processed despite being ready.
     */
    @Scheduled(cron = "0 */15 * * * *") // Every 15 minutes
    @Transactional(readOnly = true)
    public void checkStuckMessages() {
        log.debug("🔍 Checking for stuck DLQ messages");

        try {
            LocalDateTime threshold = LocalDateTime.now().minusHours(2);
            List<DlqRecordEntity> stuckMessages = dlqRecordRepository
                    .findByStatusAndCreatedAtBefore(DlqStatus.PENDING, threshold);

            if (!stuckMessages.isEmpty()) {
                log.warn("⚠️ Found {} stuck DLQ messages (pending > 2 hours)", stuckMessages.size());

                for (DlqRecordEntity message : stuckMessages) {
                    log.warn("Stuck message: id={}, topic={}, age={}",
                            message.getId(),
                            message.getTopic(),
                            ChronoUnit.HOURS.between(message.getCreatedAt(), LocalDateTime.now()));
                }

                // Record metric
                Counter.builder("dlq.messages.stuck")
                        .description("Number of stuck DLQ messages")
                        .register(meterRegistry)
                        .increment(stuckMessages.size());
            }

        } catch (Exception e) {
            log.error("Error checking stuck messages: {}", e.getMessage(), e);
        }
    }

    /**
     * Cleans up old processed messages every hour.
     * Retains messages for configured retention period (default 30 days).
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void cleanupOldMessages() {
        log.debug("🧹 Cleaning up old DLQ messages");

        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(RETENTION_DAYS);

            // Delete successfully reprocessed messages older than retention period
            long deletedCount = dlqRecordRepository
                    .deleteByStatusAndReprocessedAtBefore(DlqStatus.REPROCESSED, cutoffDate);

            if (deletedCount > 0) {
                log.info("🗑️ Cleaned up {} old DLQ messages (retention: {} days)",
                        deletedCount, RETENTION_DAYS);

                Counter.builder("dlq.messages.cleaned")
                        .description("Number of cleaned up DLQ messages")
                        .register(meterRegistry)
                        .increment(deletedCount);
            }

        } catch (Exception e) {
            log.error("Error cleaning up old messages: {}", e.getMessage(), e);
        }
    }

    /**
     * Processes a single DLQ message.
     */
    private RecoveryOutcome processMessage(DlqRecordEntity message) {
        Timer.Sample timer = Timer.start(meterRegistry);

        try {
            log.info("🔄 Processing DLQ message: id={}, topic={}, retryCount={}",
                    message.getId(), message.getTopic(), message.getRetryCount());

            // Select recovery strategy via factory
            RecoveryStrategyHandler strategy = strategyFactory.selectStrategy(message);

            // Execute recovery
            RecoveryStrategyHandler.RecoveryResult result = strategy.recover(message);

            // Update message based on result
            RecoveryOutcome outcome = updateMessageStatus(message, result, strategy);

            // Record metrics
            timer.stop(meterRegistry.timer("dlq.processor.message.time",
                    "strategy", strategy.getStrategyName(),
                    "outcome", outcome.name()));

            return outcome;

        } catch (Exception e) {
            log.error("❌ Error processing message id={}: {}",
                    message.getId(), e.getMessage(), e);

            // Update failure tracking
            message.setRetryCount(message.getRetryCount() + 1);
            message.setLastFailureTime(Instant.now());
            message.setLastFailureReason("Processing error: " + e.getMessage());
            message.setNextRetryTime(Instant.now().plus(5, ChronoUnit.MINUTES));
            dlqRecordRepository.save(message);

            timer.stop(meterRegistry.timer("dlq.processor.message.time",
                    "strategy", "UNKNOWN",
                    "outcome", "ERROR"));

            return RecoveryOutcome.RETRY_LATER;
        }
    }

    /**
     * Updates DLQ message status based on recovery result.
     */
    private RecoveryOutcome updateMessageStatus(
            DlqRecordEntity message,
            RecoveryStrategyHandler.RecoveryResult result,
            RecoveryStrategyHandler strategy) {

        if (result.success()) {
            message.setStatus(DlqStatus.REPROCESSED);
            message.setReprocessedAt(LocalDateTime.now());
            message.setReprocessingResult("Success: " + result.message());
            dlqRecordRepository.save(message);
            return RecoveryOutcome.SUCCESS;

        } else if (result.retryable()) {
            message.setRetryCount(message.getRetryCount() + 1);
            message.setLastFailureTime(Instant.now());
            message.setLastFailureReason(result.message());

            // Calculate next retry time
            int delaySeconds = result.nextRetryDelaySeconds() != null ?
                    result.nextRetryDelaySeconds() : 300;
            message.setNextRetryTime(Instant.now().plusSeconds(delaySeconds));

            dlqRecordRepository.save(message);
            return RecoveryOutcome.RETRY_LATER;

        } else {
            // Permanent failure - already handled by strategy
            // (e.g., SkipStrategy marks as PARKED, ManualIntervention creates ticket)
            return result.message().contains("skip") ?
                    RecoveryOutcome.SKIPPED :
                    RecoveryOutcome.MANUAL_REVIEW;
        }
    }

    /**
     * Records batch processing metrics.
     */
    private void recordBatchMetrics(ProcessingStats stats) {
        Counter.builder("dlq.processor.batch.success")
                .description("Successful DLQ recoveries")
                .register(meterRegistry)
                .increment(stats.successCount);

        Counter.builder("dlq.processor.batch.retry_scheduled")
                .description("DLQ messages scheduled for retry")
                .register(meterRegistry)
                .increment(stats.retryScheduledCount);

        Counter.builder("dlq.processor.batch.skipped")
                .description("DLQ messages skipped")
                .register(meterRegistry)
                .increment(stats.skippedCount);

        Counter.builder("dlq.processor.batch.manual_review")
                .description("DLQ messages requiring manual review")
                .register(meterRegistry)
                .increment(stats.manualReviewCount);
    }

    /**
     * Recovery outcome for a single message.
     */
    private enum RecoveryOutcome {
        SUCCESS,
        RETRY_LATER,
        SKIPPED,
        MANUAL_REVIEW
    }

    /**
     * Processing statistics for a batch.
     */
    private static class ProcessingStats {
        int successCount = 0;
        int retryScheduledCount = 0;
        int skippedCount = 0;
        int manualReviewCount = 0;

        void record(RecoveryOutcome outcome) {
            switch (outcome) {
                case SUCCESS -> successCount++;
                case RETRY_LATER -> retryScheduledCount++;
                case SKIPPED -> skippedCount++;
                case MANUAL_REVIEW -> manualReviewCount++;
            }
        }
    }
}

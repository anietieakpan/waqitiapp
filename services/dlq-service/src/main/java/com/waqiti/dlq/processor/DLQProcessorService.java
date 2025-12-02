package com.waqiti.dlq.processor;

import com.waqiti.dlq.model.DLQMessage;
import com.waqiti.dlq.repository.DLQMessageRepository;
import com.waqiti.dlq.strategy.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DLQ Processor Service - Main orchestrator for DLQ message recovery.
 *
 * This service runs scheduled jobs to process messages in the dead letter queue:
 * 1. Fetch messages ready for retry
 * 2. Select appropriate recovery strategy
 * 3. Execute recovery
 * 4. Update message status
 * 5. Record metrics
 *
 * PROCESSING PRIORITY:
 * - CRITICAL messages: Every minute
 * - HIGH messages: Every 5 minutes
 * - MEDIUM messages: Every 15 minutes
 * - LOW messages: Every hour
 *
 * RECOVERY STRATEGIES:
 * - AutomaticRetry: Network/timeout errors (exponential backoff)
 * - CompensatingTransaction: Financial transaction failures
 * - ManualIntervention: Complex issues requiring human review
 * - Skip: Invalid/obsolete messages
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DLQProcessorService {

    private final DLQMessageRepository dlqMessageRepository;
    private final RecoveryStrategyFactory strategyFactory;
    private final AutomaticRetryStrategy automaticRetryStrategy;
    private final CompensatingTransactionStrategy compensatingTransactionStrategy;
    private final ManualInterventionStrategy manualInterventionStrategy;
    private final SkipStrategy skipStrategy;
    private final MeterRegistry meterRegistry;

    private static final int MAX_MESSAGES_PER_BATCH = 100;

    /**
     * Process CRITICAL priority messages every minute.
     */
    @Scheduled(cron = "0 * * * * *") // Every minute
    @Transactional
    public void processCriticalMessages() {
        log.debug("Processing CRITICAL priority DLQ messages");
        processMessagesByPriority(DLQMessage.DLQPriority.CRITICAL);
    }

    /**
     * Process HIGH priority messages every 5 minutes.
     */
    @Scheduled(cron = "0 */5 * * * *") // Every 5 minutes
    @Transactional
    public void processHighPriorityMessages() {
        log.debug("Processing HIGH priority DLQ messages");
        processMessagesByPriority(DLQMessage.DLQPriority.HIGH);
    }

    /**
     * Process MEDIUM priority messages every 15 minutes.
     */
    @Scheduled(cron = "0 */15 * * * *") // Every 15 minutes
    @Transactional
    public void processMediumPriorityMessages() {
        log.debug("Processing MEDIUM priority DLQ messages");
        processMessagesByPriority(DLQMessage.DLQPriority.MEDIUM);
    }

    /**
     * Process LOW priority messages every hour.
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void processLowPriorityMessages() {
        log.debug("Processing LOW priority DLQ messages");
        processMessagesByPriority(DLQMessage.DLQPriority.LOW);
    }

    /**
     * Processes messages ready for retry based on priority.
     */
    private void processMessagesByPriority(DLQMessage.DLQPriority priority) {
        Timer.Sample timer = Timer.start(meterRegistry);

        try {
            // Fetch messages ready for retry
            LocalDateTime now = LocalDateTime.now();
            List<DLQMessage> messages = dlqMessageRepository.findMessagesReadyForRetry(now)
                .stream()
                .filter(m -> m.getPriority() == priority)
                .limit(MAX_MESSAGES_PER_BATCH)
                .toList();

            if (messages.isEmpty()) {
                log.debug("No {} priority messages ready for processing", priority);
                return;
            }

            log.info("Processing {} {} priority DLQ messages", messages.size(), priority);

            int successCount = 0;
            int failureCount = 0;
            int skipCount = 0;
            int manualReviewCount = 0;

            for (DLQMessage message : messages) {
                RecoveryOutcome outcome = processMessage(message);

                switch (outcome) {
                    case SUCCESS -> successCount++;
                    case RETRY_LATER -> failureCount++;
                    case SKIPPED -> skipCount++;
                    case MANUAL_REVIEW -> manualReviewCount++;
                }
            }

            log.info("DLQ processing completed for {}: success={}, failed={}, skipped={}, manual={}",
                    priority, successCount, failureCount, skipCount, manualReviewCount);

            // Record metrics
            recordBatchMetrics(priority, successCount, failureCount, skipCount, manualReviewCount);

            timer.stop(meterRegistry.timer("dlq.processor.batch.time", "priority", priority.toString()));

        } catch (Exception e) {
            log.error("DLQ processing batch failed for {}: error={}", priority, e.getMessage(), e);
            timer.stop(meterRegistry.timer("dlq.processor.batch.time",
                "priority", priority.toString(), "result", "error"));
        }
    }

    /**
     * Processes a single DLQ message.
     */
    private RecoveryOutcome processMessage(DLQMessage message) {
        Timer.Sample timer = Timer.start(meterRegistry);

        log.debug("Processing DLQ message: id={}, topic={}, retryCount={}",
                message.getId(), message.getOriginalTopic(), message.getRetryCount());

        try {
            // Update status to RETRYING
            message.setStatus(DLQMessage.DLQStatus.RETRYING);
            message.setLastRetryAt(LocalDateTime.now());
            dlqMessageRepository.save(message);

            // Get appropriate recovery strategy
            RecoveryStrategyHandler strategy = getRecoveryStrategy(message);

            log.debug("Using strategy: {} for message: {}", strategy.getStrategyName(), message.getId());

            // Execute recovery
            RecoveryStrategyHandler.RecoveryResult result = strategy.recover(message);

            // Handle result
            RecoveryOutcome outcome = handleRecoveryResult(message, result, strategy);

            timer.stop(meterRegistry.timer("dlq.processor.message.time",
                "strategy", strategy.getStrategyName(),
                "outcome", outcome.toString()));

            return outcome;

        } catch (Exception e) {
            log.error("DLQ message processing failed: id={}, error={}", message.getId(), e.getMessage(), e);

            handleProcessingError(message, e);

            timer.stop(meterRegistry.timer("dlq.processor.message.time",
                "outcome", "error"));

            return RecoveryOutcome.RETRY_LATER;
        }
    }

    /**
     * Selects appropriate recovery strategy for message.
     */
    private RecoveryStrategyHandler getRecoveryStrategy(DLQMessage message) {
        // Check if message should be skipped
        if (skipStrategy.canHandle(message)) {
            return skipStrategy;
        }

        // Check if automatic retry is appropriate
        if (automaticRetryStrategy.canHandle(message)) {
            return automaticRetryStrategy;
        }

        // Check if compensating transaction is needed
        if (compensatingTransactionStrategy.canHandle(message)) {
            return compensatingTransactionStrategy;
        }

        // Default to manual intervention
        return manualInterventionStrategy;
    }

    /**
     * Handles recovery result and updates message status.
     */
    private RecoveryOutcome handleRecoveryResult(
            DLQMessage message,
            RecoveryStrategyHandler.RecoveryResult result,
            RecoveryStrategyHandler strategy) {

        if (result.success()) {
            // Recovery successful
            message.setStatus(DLQMessage.DLQStatus.RECOVERED);
            message.setRecoveredAt(LocalDateTime.now());
            message.setRecoveryNotes("Recovered using " + strategy.getStrategyName() + ": " + result.message());
            dlqMessageRepository.save(message);

            log.info("✅ DLQ message recovered: id={}, strategy={}", message.getId(), strategy.getStrategyName());
            return RecoveryOutcome.SUCCESS;

        } else if (result.retryable()) {
            // Schedule for retry
            message.setRetryCount(message.getRetryCount() + 1);
            message.setNextRetryAt(LocalDateTime.now().plusSeconds(result.nextRetryDelaySeconds()));
            message.setStatus(DLQMessage.DLQStatus.READY_FOR_RETRY);
            message.setRecoveryNotes("Retry scheduled: " + result.message());
            dlqMessageRepository.save(message);

            log.info("⏳ DLQ message scheduled for retry: id={}, nextRetry={}, delay={}s",
                    message.getId(), message.getNextRetryAt(), result.nextRetryDelaySeconds());
            return RecoveryOutcome.RETRY_LATER;

        } else {
            // Permanent failure or manual intervention required
            if (strategy instanceof ManualInterventionStrategy) {
                message.setStatus(DLQMessage.DLQStatus.MANUAL_REVIEW_REQUIRED);
                dlqMessageRepository.save(message);

                log.info("⚠️ DLQ message requires manual review: id={}", message.getId());
                return RecoveryOutcome.MANUAL_REVIEW;

            } else if (strategy instanceof SkipStrategy) {
                message.setStatus(DLQMessage.DLQStatus.SKIPPED);
                message.setRecoveredAt(LocalDateTime.now());
                dlqMessageRepository.save(message);

                log.info("⏭️ DLQ message skipped: id={}, reason={}", message.getId(), result.message());
                return RecoveryOutcome.SKIPPED;

            } else {
                message.setStatus(DLQMessage.DLQStatus.PERMANENT_FAILURE);
                message.setRecoveryNotes("Permanent failure: " + result.message());
                dlqMessageRepository.save(message);

                log.warn("❌ DLQ message permanent failure: id={}", message.getId());
                return RecoveryOutcome.MANUAL_REVIEW;
            }
        }
    }

    /**
     * Handles processing errors.
     */
    private void handleProcessingError(DLQMessage message, Exception error) {
        message.setRetryCount(message.getRetryCount() + 1);
        message.setNextRetryAt(LocalDateTime.now().plusSeconds(300)); // Retry in 5 minutes
        message.setStatus(DLQMessage.DLQStatus.READY_FOR_RETRY);
        message.setRecoveryNotes("Processing error: " + error.getMessage());
        dlqMessageRepository.save(message);
    }

    /**
     * Records batch processing metrics.
     */
    private void recordBatchMetrics(
            DLQMessage.DLQPriority priority,
            int successCount,
            int failureCount,
            int skipCount,
            int manualReviewCount) {

        String priorityTag = priority.toString();

        Counter.builder("dlq.processor.batch.processed")
            .tag("priority", priorityTag)
            .tag("outcome", "success")
            .register(meterRegistry)
            .increment(successCount);

        Counter.builder("dlq.processor.batch.processed")
            .tag("priority", priorityTag)
            .tag("outcome", "retry")
            .register(meterRegistry)
            .increment(failureCount);

        Counter.builder("dlq.processor.batch.processed")
            .tag("priority", priorityTag)
            .tag("outcome", "skipped")
            .register(meterRegistry)
            .increment(skipCount);

        Counter.builder("dlq.processor.batch.processed")
            .tag("priority", priorityTag)
            .tag("outcome", "manual")
            .register(meterRegistry)
            .increment(manualReviewCount);
    }

    /**
     * Recovery outcome enum.
     */
    private enum RecoveryOutcome {
        SUCCESS,
        RETRY_LATER,
        SKIPPED,
        MANUAL_REVIEW
    }
}
